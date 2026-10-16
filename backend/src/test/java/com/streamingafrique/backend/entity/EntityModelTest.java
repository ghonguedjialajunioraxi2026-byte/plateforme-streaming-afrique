package com.streamingafrique.backend.entity;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vérifie que le GRAFCHE D'ENTITÉS (User → CreatorProfile → Stream/Video/Clip,
 * Follow, Notification) se persiste et se recharge correctement (H2 en mémoire),
 * et que les CONTRAINTES de la base sont bien actives.
 */
@SpringBootTest
@Transactional
class EntityModelTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CreatorProfileRepository creatorProfileRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private StreamRepository streamRepository;
    @Autowired
    private VideoRepository videoRepository;
    @Autowired
    private ClipRepository clipRepository;
    @Autowired
    private FollowRepository followRepository;
    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void le_graphe_complet_se_persiste_et_se_recharge() {
        // 1. Compte + fiche créateur (relation 1-1 pour l'instant unidirectionnelle)
        User ada = userRepository.save(User.create("ada@example.com", "Ada Lovelace", "hash-bcrypt"));
        CreatorProfile channel = creatorProfileRepository.save(
                CreatorProfile.create(ada, "Ada & ses machines", "ada-channel"));

        // 2. Catégorie "métier"
        Category tech = categoryRepository.save(Category.create("Technique", "technique", "Dev et science"));

        // 3. Direct planifié
        Stream live = streamRepository.save(
                Stream.schedule(channel, "En direct : déployer Spring Boot", tech,
                        Instant.now().plusSeconds(3600)));
        live.markStarted();   // SCHEDULED → LIVE
        streamRepository.save(live);

        // 4. Vidéo VOD
        Video replay = videoRepository.save(Video.publish(channel, "Tuto JPA en 10 min", "Mapping d'entités",
                tech, "https://cdn.example.com/jpa.mp4", "https://cdn.example.com/jpa.jpg", 600));

        // 5. Clip extrait du direct + clip extrait de la vidéo
        Clip clipStream = clipRepository.save(Clip.fromStream(channel, live, "Meilleur moment du live",
                "https://cdn.example.com/clip1.mp4", 30));
        Clip clipVideo = clipRepository.save(Clip.fromVideo(channel, replay, "Extrait du tutoriel",
                "https://cdn.example.com/clip2.mp4", 45));

        // 6. Suivi + notification
        followRepository.save(Follow.create(ada, channel));
        notificationRepository.save(Notification.of(ada, Notification.Type.NEW_FOLLOWER,
                "Nouvel abonné", "Qui veut vous suivre vous suit", "/@ada-channel"));

        // Vérifications de rechargement (les relations LAZY chargent à l'accès)
        CreatorProfile reloaded = creatorProfileRepository.findById(channel.getId()).orElseThrow();
        assertEquals("ada-channel", reloaded.getSlug());
        assertNotNull(reloaded.getId());
        // la relation User réapparaît (pas de perte d'association)
        assertEquals(ada.getEmail(), reloaded.getUser().getEmail());

        Stream reloadedLive = streamRepository.findById(live.getId()).orElseThrow();
        assertEquals(Stream.Status.LIVE, reloadedLive.getStatus());
        assertEquals("ada & ses machines", reloadedLive.getCreator().getDisplayName().toLowerCase());

        Video reloadedVideo = videoRepository.findById(replay.getId()).orElseThrow();
        assertEquals(tech.getId(), reloadedVideo.getCategory().getId());
        assertEquals(600, reloadedVideo.getDurationSeconds());

        Clip reloadedClipStream = clipRepository.findById(clipStream.getId()).orElseThrow();
        assertNotNull(reloadedClipStream.getStream());
        assertEquals(live.getId(), reloadedClipStream.getStream().getId());

        Clip reloadedClipVideo = clipRepository.findById(clipVideo.getId()).orElseThrow();
        assertNotNull(reloadedClipVideo.getVideo());
        assertEquals(replay.getId(), reloadedClipVideo.getVideo().getId());

        Notification notification = notificationRepository.findAll().get(0);
        assertFalse(notification.isRead());
        notification.markAsRead();
        notificationRepository.save(notification);
        assertTrue(notificationRepository.findById(notification.getId()).orElseThrow().isRead());

        // l'audit est appliqué à TOUTES les entités
        assertNotNull(ada.getCreatedAt());
        assertNotNull(live.getUpdatedAt());
    }

    @Test
    void email_unique_est_refuse_par_la_base() {
        userRepository.saveAndFlush(User.create("duplicata@example.com", "Premier", "hash"));

        assertThrows(DataIntegrityViolationException.class,
                () -> userRepository.saveAndFlush(User.create("duplicata@example.com", "Doublon", "hash")));
    }
}