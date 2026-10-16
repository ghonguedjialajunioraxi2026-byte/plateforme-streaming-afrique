package com.streamingafrique.backend.repository;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.category.model.Category;
import com.streamingafrique.backend.category.repository.CategoryRepository;
import com.streamingafrique.backend.clip.model.Clip;
import com.streamingafrique.backend.clip.repository.ClipRepository;
import com.streamingafrique.backend.creator.model.CreatorProfile;
import com.streamingafrique.backend.creator.repository.CreatorProfileRepository;
import com.streamingafrique.backend.follow.model.Follow;
import com.streamingafrique.backend.follow.repository.FollowRepository;
import com.streamingafrique.backend.notification.model.Notification;
import com.streamingafrique.backend.notification.repository.NotificationRepository;
import com.streamingafrique.backend.stream.model.Stream;
import com.streamingafrique.backend.stream.repository.StreamRepository;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;
import com.streamingafrique.backend.video.model.Video;
import com.streamingafrique.backend.video.repository.VideoRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teste TOUTES les opérations principales des repositories (H2, transaction
 * annulée après chaque test). Pas de logique métier testée ici — juste
 * l'accès aux données : recherche, comptage, pagination et tri.
 */
@SpringBootTest
@Transactional
class RepositoryTest {

    @Autowired
    private UserRepository users;
    @Autowired
    private CreatorProfileRepository creators;
    @Autowired
    private CategoryRepository categories;
    @Autowired
    private StreamRepository streams;
    @Autowired
    private VideoRepository videos;
    @Autowired
    private ClipRepository clips;
    @Autowired
    private FollowRepository follows;
    @Autowired
    private NotificationRepository notifications;

    /** Graphe minimal partagé par les tests. */
    private record Fixture(User user, CreatorProfile channel, Category tech,
                           Stream live, Stream scheduled, Video replay,
                           Clip clipStream, Clip clipVideo) {}

    private Fixture fixture() {
        User user = users.save(User.create("ada@example.com", "Ada", "hash"));
        CreatorProfile channel = creators.save(CreatorProfile.create(user, "Ada & machines", "ada"));
        Category tech = categories.save(Category.create("Technique", "technique", "Dev et science"));

        Stream live = streams.save(Stream.schedule(channel, "Live magie", tech, Instant.now()));
        live.markStarted();
        streams.save(live);
        Stream scheduled = streams.save(Stream.schedule(channel, "Live demain", tech, Instant.now().plusSeconds(86400)));

        Video replay = videos.save(Video.publish(channel, "Tuto JPA", "Mapping", tech,
                "https://cdn.example.com/v.mp4", "https://cdn.example.com/v.jpg", 600));
        Clip clipStream = clips.save(Clip.fromStream(channel, live, "Meilleur moment", "https://cdn.example.com/c1.mp4", 30));
        Clip clipVideo = clips.save(Clip.fromVideo(channel, replay, "Extrait tuto", "https://cdn.example.com/c2.mp4", 45));

        follows.save(Follow.create(user, channel));
        notifications.save(Notification.of(user, Notification.Type.NEW_FOLLOWER,
                "Nouvel abonné", "Qui vous suit veut vous suivre", "/@ada"));

        return new Fixture(user, channel, tech, live, scheduled, replay, clipStream, clipVideo);
    }

    @Test
    void user_recherche_par_email_et_unicite() {
        User grace = users.save(User.create("grace@example.com", "Grace", "hash"));

        assertEquals("grace@example.com", users.findByEmail("grace@example.com").orElseThrow().getEmail());
        assertTrue(users.findByEmail("inconnu@example.com").isEmpty());
        assertTrue(users.existsByEmail("grace@example.com"));
        assertFalse(users.existsByEmail("autre@example.com"));
        assertEquals(grace.getId(), users.findByEmail("grace@example.com").orElseThrow().getId());
    }

    @Test
    void creator_profile_par_slug_et_par_user() {
        Fixture f = fixture();

        assertEquals("ada", creators.findBySlug("ada").orElseThrow().getSlug());
        assertTrue(creators.findBySlug("n-existe-pas").isEmpty());
        assertEquals(f.channel.getId(), creators.findByUserId(f.user.getId()).orElseThrow().getId());
        assertTrue(creators.existsBySlug("ada"));
        assertFalse(creators.existsBySlug("libre"));
    }

    @Test
    void stream_par_statut_et_par_createur_pagine() {
        Fixture f = fixture();

        Page<Stream> live = streams.findByStatus(Stream.Status.LIVE, PageRequest.of(0, 10));
        assertEquals(1, live.getTotalElements());
        assertEquals("Live magie", live.getContent().get(0).getTitle());

        Page<Stream> ofChannel = streams.findByCreatorId(f.channel.getId(),
                PageRequest.of(0, 1, Sort.by("title")));
        assertEquals(2, ofChannel.getTotalElements());   // live + programmé
        assertEquals(1, ofChannel.getNumberOfElements()); // taille de page 1
        assertTrue(f.scheduled.getStartedAt() == null
                || f.scheduled.getStartedAt().isBefore(Instant.now().plusSeconds(86400)));
    }

    @Test
    void video_par_visibilite_et_par_categorie() {
        Fixture f = fixture();

        assertEquals(1, videos.findByVisibility(Video.Visibility.PUBLIC, PageRequest.of(0, 10)).getTotalElements());
        assertEquals(0, videos.findByVisibility(Video.Visibility.PRIVATE, PageRequest.of(0, 10)).getTotalElements());
        assertEquals(1, videos.findByCategoryId(f.tech.getId(), PageRequest.of(0, 10)).getTotalElements());
        assertEquals("Tuto JPA", videos.findByCategoryId(f.tech.getId(), PageRequest.of(0, 10))
                .getContent().get(0).getTitle());
    }

    @Test
    void clip_par_source_direct_ou_video() {
        Fixture f = fixture();

        Page<Clip> fromStream = clips.findByStreamId(f.live.getId(), PageRequest.of(0, 10));
        assertEquals(1, fromStream.getTotalElements());
        assertEquals("Meilleur moment", fromStream.getContent().get(0).getTitle());

        Page<Clip> fromVideo = clips.findByVideoId(f.replay.getId(), PageRequest.of(0, 10));
        assertEquals(1, fromVideo.getTotalElements());
        assertEquals("Extrait tuto", fromVideo.getContent().get(0).getTitle());

        assertEquals(2, clips.findByCreatorId(f.channel.getId(), PageRequest.of(0, 10)).getTotalElements());
        assertEquals(2, clips.findByVisibility(Clip.Visibility.PUBLIC, PageRequest.of(0, 10)).getTotalElements());
    }

    @Test
    void follow_existence_compteurs_et_chaines_suivies() {
        Fixture f = fixture();

        assertTrue(follows.existsByFollowerIdAndFollowedId(f.user.getId(), f.channel.getId()));
        assertFalse(follows.existsByFollowerIdAndFollowedId(9999L, f.channel.getId()));
        assertEquals(1, follows.countByFollowedId(f.channel.getId()));
        assertEquals(1, follows.countByFollowerId(f.user.getId()));

        Page<CreatorProfile> followedPage =
                follows.findFollowedProfilesByFollowerId(f.user.getId(), PageRequest.of(0, 10));
        assertEquals(1, followedPage.getTotalElements());
        assertEquals("ada", followedPage.getContent().get(0).getSlug());
    }

    @Test
    void notification_non_lues_badge_et_tout_lu() {
        Fixture f = fixture();

        assertEquals(1, notifications.countByRecipientIdAndIsReadFalse(f.user.getId()));

        Page<Notification> center = notifications.findByRecipientIdOrderByCreatedAtDesc(
                f.user.getId(), PageRequest.of(0, 10));
        assertEquals(1, center.getTotalElements());
        assertEquals(f.user.getId(), center.getContent().get(0).getRecipient().getId());

        int updated = notifications.markAllAsRead(f.user.getId());
        assertEquals(1, updated);
        assertEquals(0, notifications.countByRecipientIdAndIsReadFalse(f.user.getId()));
    }
}