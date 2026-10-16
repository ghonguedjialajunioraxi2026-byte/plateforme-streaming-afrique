package com.streamingafrique.backend.video;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.streamingafrique.backend.clip.model.Clip;
import com.streamingafrique.backend.clip.repository.ClipRepository;
import com.streamingafrique.backend.creator.model.CreatorProfile;
import com.streamingafrique.backend.creator.repository.CreatorProfileRepository;
import com.streamingafrique.backend.notification.model.Notification;
import com.streamingafrique.backend.notification.repository.NotificationRepository;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;
import com.streamingafrique.backend.video.model.Video;
import com.streamingafrique.backend.video.repository.VideoRepository;
import com.streamingafrique.backend.video.service.MediaProcessingWorker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Étape 15 — chemin ASYNC réel (SANS transaction englobante, comme en
 * production : le worker tourne dans son thread et sa transaction).
 *
 * Non-régression : sans {@code @Transactional} sur les points d'entrée
 * {@code @Async}, tout accès lazy levait {@code LazyInitializationException}
 * (clips/VOD coincés en PROCESSING) — les tests {@code @Transactional}
 * masquaient le défaut. Ici on appelle les enveloppes async et on sonde
 * jusqu'au READY (timeout 15 s).
 */
@SpringBootTest
class MediaProcessingAsyncTest {

    @Autowired
    private MediaProcessingWorker processing;
    @Autowired
    private VideoRepository videos;
    @Autowired
    private ClipRepository clips;
    @Autowired
    private UserRepository users;
    @Autowired
    private CreatorProfileRepository channels;
    @Autowired
    private NotificationRepository notifications;

    @AfterEach
    void cleanUp() {
        clips.deleteAll();
        videos.deleteAll();
        notifications.deleteAll();
        channels.deleteAll();
        users.deleteAll();
    }

    @Test
    void traitementVodAsyncAboutitSansTransactionEnglobante() {
        User owner = users.save(User.create("async15@example.com", "async15", "hash"));
        CreatorProfile channel = channels.save(CreatorProfile.create(owner, "Async TV", "async15"));
        Video video = videos.save(Video.publish(channel, "VOD async", null, null,
                "https://cdn.example.com/a.mp4", null, 120));
        video.changeProcessingStatus(Video.ProcessingStatus.PROCESSING);
        videos.save(video);

        processing.processVideoAsync(video.getId());

        Video ready = pollVideo(video.getId());
        assertEquals(Video.ProcessingStatus.READY, ready.getProcessingStatus());
        assertTrue(notifications.countByRecipientIdAndIsReadFalse(owner.getId()) >= 1);
    }

    @Test
    void traitementClipAsyncAboutitSansTransactionEnglobante() {
        User owner = users.save(User.create("asyncclip15@example.com", "asyncclip15", "hash"));
        CreatorProfile channel = channels.save(CreatorProfile.create(owner, "Async Clips", "asyncclip15"));
        Video video = videos.save(Video.publish(channel, "Source", null, null,
                "https://cdn.example.com/s.mp4", null, 600));
        Clip clip = clips.save(Clip.fromVideo(channel, video, "Extrait",
                "https://cdn.example.com/c.mp4", 20));
        clip.changeProcessingStatus(Clip.ProcessingStatus.PROCESSING);
        clips.save(clip);

        processing.processClipAsync(clip.getId());

        Clip ready = pollClip(clip.getId());
        assertEquals(Clip.ProcessingStatus.READY, ready.getProcessingStatus());
        // Sans transaction, la notification (lazy chaîne → compte) échouerait :
        // le READY seul ne prouve pas le chemin complet.
        assertTrue(notifications.countByRecipientIdAndIsReadFalse(owner.getId()) >= 1);
    }

    private Video pollVideo(Long id) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        while (Instant.now().isBefore(deadline)) {
            Video video = videos.findById(id).orElseThrow();
            if (video.getProcessingStatus() == Video.ProcessingStatus.READY) {
                return video;
            }
            sleep();
        }
        return videos.findById(id).orElseThrow();
    }

    private Clip pollClip(Long id) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        while (Instant.now().isBefore(deadline)) {
            Clip clip = clips.findById(id).orElseThrow();
            if (clip.getProcessingStatus() == Clip.ProcessingStatus.READY) {
                return clip;
            }
            sleep();
        }
        return clips.findById(id).orElseThrow();
    }

    private static void sleep() {
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
