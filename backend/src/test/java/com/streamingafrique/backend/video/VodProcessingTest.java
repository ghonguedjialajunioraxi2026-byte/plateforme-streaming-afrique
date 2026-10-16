package com.streamingafrique.backend.video;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.creator.model.CreatorProfile;
import com.streamingafrique.backend.creator.repository.CreatorProfileRepository;
import com.streamingafrique.backend.notification.model.Notification;
import com.streamingafrique.backend.notification.repository.NotificationRepository;
import com.streamingafrique.backend.stream.model.Stream;
import com.streamingafrique.backend.stream.repository.StreamRepository;
import com.streamingafrique.backend.stream.service.StreamService;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;
import com.streamingafrique.backend.video.model.Video;
import com.streamingafrique.backend.video.repository.VideoRepository;
import com.streamingafrique.backend.video.service.MediaProcessingWorker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Étape 15 — VOD automatique (H2, transaction annulée).
 *
 * Couvre : fin de live → VOD PROCESSING non publiée (jamais de READY
 * fantôme), worker synchrone → READY + publiée + notification NEW_VIDEO,
 * sans enregistrement → FAILED (jamais READY sans fichier).
 */
@SpringBootTest
@Transactional
class VodProcessingTest {

    @Autowired
    private StreamService streams;
    @Autowired
    private StreamRepository streamRepository;
    @Autowired
    private VideoRepository videos;
    @Autowired
    private MediaProcessingWorker processing;
    @Autowired
    private UserRepository users;
    @Autowired
    private CreatorProfileRepository channels;
    @Autowired
    private NotificationRepository notifications;

    private CreatorProfile channel;
    private CurrentUser actor;

    private void setUp(String email, String slug) {
        User user = users.save(User.create(email, slug, "hash"));
        user.changeRole(User.Role.CREATOR);
        channel = channels.save(CreatorProfile.create(user, slug + " TV", slug));
        actor = CurrentUser.of(user.getId(), user.getEmail(), user.getRole());
    }

    private Stream live(boolean withPlayback) {
        Stream stream = streamRepository.save(
                Stream.schedule(channel, "Grand live", null, Instant.now()));
        stream.markStarted();
        if (withPlayback) {
            stream.attachIngest("hash", Stream.IngestProtocol.RTMP,
                    "https://cdn.example.com/live.m3u8");
        }
        return streamRepository.saveAndFlush(stream);
    }

    @Test
    void finDeLiveCreeUneVodEnTraitementNonPubliee() {
        setUp("vodauto@example.com", "vodauto");
        Stream ended = streamRepository.findById(live(true).getId()).orElseThrow();

        streams.end(ended.getId(), actor);

        Video vod = videos.findAll().stream()
                .filter(v -> v.getOriginStream() != null
                        && v.getOriginStream().getId().equals(ended.getId()))
                .findFirst().orElseThrow();
        assertEquals(Video.ProcessingStatus.PROCESSING, vod.getProcessingStatus());
        assertNull(vod.getPublishedAt());
        assertEquals("Grand live", vod.getTitle());
    }

    @Test
    void workerPasseReadyEtNotifieQuandFichierPresent() {
        setUp("vodready@example.com", "vodready");
        Stream ended = streamRepository.findById(live(true).getId()).orElseThrow();
        streams.end(ended.getId(), actor);
        Video vod = videos.findAll().stream()
                .filter(v -> v.getOriginStream() != null)
                .findFirst().orElseThrow();

        processing.processVideoNow(vod.getId());

        Video ready = videos.findById(vod.getId()).orElseThrow();
        assertEquals(Video.ProcessingStatus.READY, ready.getProcessingStatus());
        assertNotNull(ready.getPublishedAt());
        assertTrue(notifications.findByRecipientIdOrderByCreatedAtDesc(
                channel.getUser().getId(),
                org.springframework.data.domain.PageRequest.of(0, 10)).stream()
                .anyMatch(n -> n.getType() == Notification.Type.NEW_VIDEO));
    }

    @Test
    void sansEnregistrementLeWorkerMarqueFailedJamaisReady() {
        setUp("vodfailed@example.com", "vodfailed");
        Stream ended = streamRepository.findById(live(false).getId()).orElseThrow();
        streams.end(ended.getId(), actor);
        Video vod = videos.findAll().stream()
                .filter(v -> v.getOriginStream() != null)
                .findFirst().orElseThrow();

        processing.processVideoNow(vod.getId());

        assertEquals(Video.ProcessingStatus.FAILED,
                videos.findById(vod.getId()).orElseThrow().getProcessingStatus());
        assertNull(videos.findById(vod.getId()).orElseThrow().getPublishedAt());
    }
}
