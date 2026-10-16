package com.streamingafrique.backend.service;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.category.dto.CategoryRequest;
import com.streamingafrique.backend.category.service.CategoryService;
import com.streamingafrique.backend.clip.dto.ClipDto;
import com.streamingafrique.backend.clip.dto.ClipRequest;
import com.streamingafrique.backend.clip.service.ClipService;
import com.streamingafrique.backend.creator.dto.CreatorProfileDto;
import com.streamingafrique.backend.creator.dto.CreatorProfileRequest;
import com.streamingafrique.backend.creator.service.CreatorService;
import com.streamingafrique.backend.exception.ApiException;
import com.streamingafrique.backend.follow.dto.FollowDto;
import com.streamingafrique.backend.follow.service.FollowService;
import com.streamingafrique.backend.notification.service.NotificationService;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.stream.dto.StreamDto;
import com.streamingafrique.backend.stream.dto.StreamRequest;
import com.streamingafrique.backend.stream.model.Stream;
import com.streamingafrique.backend.stream.service.StreamService;
import com.streamingafrique.backend.user.dto.UserDto;
import com.streamingafrique.backend.user.dto.UserRequest;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.service.UserService;
import com.streamingafrique.backend.video.dto.VideoDto;
import com.streamingafrique.backend.video.dto.VideoRequest;
import com.streamingafrique.backend.video.model.Video;
import com.streamingafrique.backend.video.service.VideoService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parcourt le scénario métier complet via les SERVICES (couche Controller
 * exclue volontairement : elle ne fait qu'acheminer). H2, transaction annulée.
 *
 * Étape 6.25 : chaque action sensible reçoit son ACTEUR ({@link CurrentUser})
 * comme les vrais contrôleurs le feraient — le service prouve propriété/rôle
 * même hors HTTP.
 */
@SpringBootTest
@Transactional
class ServiceTest {

    @Autowired
    private UserService userService;
    @Autowired
    private CreatorService creatorService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private StreamService streamService;
    @Autowired
    private VideoService videoService;
    @Autowired
    private ClipService clipService;
    @Autowired
    private FollowService followService;
    @Autowired
    private NotificationService notificationService;

    private final PageRequest page0 = PageRequest.of(0, 10);

    /** L'acteur authentifié portant l'identité et le rôle du compte. */
    private CurrentUser actor(UserDto user) {
        return CurrentUser.of(user.id(), user.email(), user.role());
    }

    @Test
    void parcours_user_createur_et_conflit_email() {
        UserDto creator = userService.create(new UserRequest("ada@example.com", "Ada", null, null));
        assertNotNull(creator.id());
        assertEquals(User.Role.USER, creator.role());     // créé utilisateur simple
        assertEquals("ada@example.com", creator.email());

        assertThrows(ApiException.class,
                () -> userService.create(new UserRequest("ADA@example.com ", "Doublon", null, null)),
                "email normalisé (minuscules) déjà pris");

        // Création de la chaîne → promotion CREATOR + slug auto
        CreatorProfileDto channel = creatorService.create(creator.id(),
                new CreatorProfileRequest("Étapes & Astuces", "Dev", null, null), actor(creator));
        assertEquals("etapes-astuces", channel.slug());
        assertEquals(User.Role.CREATOR,
                userService.getById(creator.id(), actor(creator)).role());
        assertEquals("Étapes & Astuces", channel.displayName());

        // Modification de profil → slug recalculé
        CreatorProfileDto renamed = creatorService.update(channel.id(),
                new CreatorProfileRequest("Astuces du Dev", "Dev", null, null), actor(creator));
        assertEquals("astuces-du-dev", renamed.slug());
    }

    @Test
    void parcours_stream_cycle_de_vie_proteger() {
        UserDto owner = userService.create(new UserRequest("live@example.com", "Streamer", null, null));
        CreatorProfileDto channel = creatorService.create(owner.id(),
                new CreatorProfileRequest("Directs chauds", null, null, null), actor(owner));
        long categoryId = categoryService.create(new CategoryRequest("Gaming", null)).id();

        StreamDto scheduled = streamService.schedule(channel.id(),
                new StreamRequest("Tournoi", categoryId, Instant.now().plusSeconds(7200)), actor(owner));
        assertEquals(Stream.Status.SCHEDULED, scheduled.status());
        assertNotNull(scheduled.categoryId());

        StreamDto live = streamService.start(scheduled.id(), actor(owner));
        assertEquals(Stream.Status.LIVE, live.status());
        assertNotNull(live.startedAt());

        // un LIVE ne se supprime pas et ne se modifie pas
        assertThrows(ApiException.class, () -> streamService.delete(live.id(), actor(owner)));
        assertThrows(ApiException.class,
                () -> streamService.update(live.id(), new StreamRequest("Tricheur", null, null), actor(owner)));

        StreamDto ended = streamService.end(live.id(), actor(owner));
        assertEquals(Stream.Status.ENDED, ended.status());
        assertNotNull(ended.endedAt());

        streamService.delete(ended.id(), actor(owner));     // terminé → supprimable
        assertTrue(streamService.getAll(page0).content().isEmpty());
    }

    @Test
    void parcours_video_et_feed_public() {
        UserDto owner = userService.create(new UserRequest("video@example.com", "Videaste", null, null));
        CreatorProfileDto channel = creatorService.create(owner.id(),
                new CreatorProfileRequest("Vidéos & VOD", null, null, null), actor(owner));

        VideoDto vod = videoService.publish(channel.id(), new VideoRequest(
                "Tuto JPA", "Mapping OK", null, "https://cdn.example.com/a.mp4", null, 540),
                actor(owner));
        assertEquals(Video.Visibility.PUBLIC, vod.visibility());
        assertEquals(540, vod.durationSeconds());

        videoService.changeVisibility(vod.id(), Video.Visibility.PRIVATE, actor(owner));
        assertTrue(videoService.getPublicFeed(page0).content().isEmpty()); // retiré du feed

        videoService.changeVisibility(vod.id(), Video.Visibility.PUBLIC, actor(owner));
        assertEquals(1, videoService.getPublicFeed(page0).totalElements());

        VideoDto edited = videoService.update(vod.id(), new VideoRequest(
                "Tuto JPA (v2)", "Révisé", null, "https://cdn.example.com/a.mp4", null, 620),
                actor(owner));
        assertEquals("Tuto JPA (v2)", edited.title());
    }

    @Test
    void parcours_clip_et_droits_auteur() {
        UserDto owner = userService.create(new UserRequest("clips@example.com", "Clips en chef", null, null));
        CreatorProfileDto channel = creatorService.create(owner.id(),
                new CreatorProfileRequest("Clips&Co", null, null, null), actor(owner));
        UserDto pirateUser = userService.create(new UserRequest("pirate@example.com", "Pirate", null, null));
        CreatorProfileDto intruder = creatorService.create(pirateUser.id(),
                new CreatorProfileRequest("Chaîne pirate", null, null, null), actor(pirateUser));

        StreamDto stream = streamService.schedule(channel.id(),
                new StreamRequest("Grand direct", null, null), actor(owner));
        streamService.start(stream.id(), actor(owner));

        ClipDto clip = clipService.createFromStream(channel.id(), stream.id(),
                new ClipRequest("Meilleur moment", "https://cdn.example.com/c.mp4", null, 25), actor(owner));
        assertEquals(channel.id(), clip.creatorId());
        assertEquals(stream.id(), clip.streamId());

        // Seul le créateur du direct peut le découper en clip
        assertThrows(ApiException.class,
                () -> clipService.createFromStream(intruder.id(), stream.id(),
                        new ClipRequest("Volé", "https://evil.com/x.mp4", null, 10), actor(pirateUser)));
    }

    @Test
    void parcours_follow_et_notification() {
        UserDto creator = userService.create(new UserRequest("créa@example.com", "Créa", null, null));
        CreatorProfileDto channel = creatorService.create(creator.id(),
                new CreatorProfileRequest("Créa TV", null, null, null), actor(creator));
        UserDto viewer = userService.create(new UserRequest("viewer@example.com", "Viewer", null, null));

        assertThrows(ApiException.class, () -> followService.follow(creator.id(), channel.id(), actor(creator)));
        assertThrows(ApiException.class, () -> followService.follow(9999L, channel.id(),
                CurrentUser.of(9999L, "ghost@example.com", User.Role.USER)));

        FollowDto follow = followService.follow(viewer.id(), channel.id(), actor(viewer));
        assertNotNull(follow.id());
        assertTrue(followService.isFollowing(viewer.id(), channel.id()));
        assertEquals(1, followService.countFollowers(channel.id()));

        // suivre deux fois = ignoré (idempotent)
        followService.follow(viewer.id(), channel.id(), actor(viewer));
        assertEquals(1, followService.countFollowers(channel.id()));

        // la notification "nouvel abonné" a été créée pour le propriétaire
        // (SEC-001 : lectures = destinataire ou ADMIN)
        assertEquals(1, notificationService.unreadCount(creator.id(), actor(creator)));
        assertEquals(1, notificationService.forUser(creator.id(), page0, actor(creator)).totalElements());

        assertEquals(1, notificationService.markAllAsRead(creator.id(), actor(creator)));
        assertEquals(0, notificationService.unreadCount(creator.id(), actor(creator)));

        assertEquals(1, followService.getFollowedChannels(viewer.id(), page0).totalElements());

        followService.unfollow(viewer.id(), channel.id(), actor(viewer));
        assertFalse(followService.isFollowing(viewer.id(), channel.id()));
        assertThrows(ApiException.class,
                () -> followService.unfollow(viewer.id(), channel.id(), actor(viewer)));
    }
}