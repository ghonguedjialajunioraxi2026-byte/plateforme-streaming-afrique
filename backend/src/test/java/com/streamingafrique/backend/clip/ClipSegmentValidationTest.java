package com.streamingafrique.backend.clip;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.clip.dto.ClipDto;
import com.streamingafrique.backend.clip.dto.CreateClipSegmentRequest;
import com.streamingafrique.backend.clip.model.Clip;
import com.streamingafrique.backend.clip.repository.ClipRepository;
import com.streamingafrique.backend.clip.service.ClipService;
import com.streamingafrique.backend.creator.model.CreatorProfile;
import com.streamingafrique.backend.creator.repository.CreatorProfileRepository;
import com.streamingafrique.backend.exception.ApiException;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.stream.model.Stream;
import com.streamingafrique.backend.stream.repository.StreamRepository;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;
import com.streamingafrique.backend.video.model.Video;
import com.streamingafrique.backend.video.repository.VideoRepository;
import com.streamingafrique.backend.video.service.MediaProcessingWorker;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Étape 15 — découpage viewer (H2, transaction annulée).
 *
 * Couvre : segment valide (PROCESSING, auteur tracé, visibilité héritée),
 * bornes/durées invalides (400), source privée/en traitement/inexistante,
 * worker → READY, suppression logique, non-authentifié (403).
 */
@SpringBootTest
@Transactional
class ClipSegmentValidationTest {

    @Autowired
    private ClipService clips;
    @Autowired
    private ClipRepository clipRepository;
    @Autowired
    private MediaProcessingWorker processing;
    @Autowired
    private UserRepository users;
    @Autowired
    private CreatorProfileRepository channels;
    @Autowired
    private VideoRepository videos;
    @Autowired
    private StreamRepository streams;

    private User owner;
    private CurrentUser ownerActor;
    private CreatorProfile channel;
    private User viewer;
    private CurrentUser viewerActor;
    private Video video;

    private void setUp() {
        owner = users.save(User.create("source15@example.com", "source15", "hash"));
        owner.changeRole(User.Role.CREATOR);
        ownerActor = CurrentUser.of(owner.getId(), owner.getEmail(), owner.getRole());
        channel = channels.save(CreatorProfile.create(owner, "Source TV", "source15"));
        viewer = users.save(User.create("viewer15@example.com", "viewer15", "hash"));
        viewerActor = CurrentUser.of(viewer.getId(), viewer.getEmail(), viewer.getRole());
        video = videos.save(Video.publish(channel, "VOD longue", "Desc", null,
                "https://cdn.example.com/v.mp4", null, 3600));
    }

    private CreateClipSegmentRequest segment(int start, int duration) {
        return new CreateClipSegmentRequest("Moment", start, duration, null);
    }

    @Test
    void segmentValideNaîtEnTraitementAvecAuteurTrace() {
        setUp();

        ClipDto clip = clips.createFromVideoSegment(video.getId(), segment(3500, 25), viewerActor);

        assertEquals(Clip.ProcessingStatus.PROCESSING, clip.processingStatus());
        assertEquals(3500, clip.startsAtSeconds());
        assertEquals(25, clip.durationSeconds());
        assertEquals(channel.getId(), clip.creatorId());
        assertEquals(viewer.getId(), clip.createdByUserId());
        assertEquals(Clip.Visibility.PUBLIC, clip.visibility());
        assertEquals(video.getId(), clip.videoId());
        assertNotNull(clip.shareSlug());
    }

    @Test
    void workerPasseLeClipReady() {
        setUp();
        ClipDto clip = clips.createFromVideoSegment(video.getId(), segment(100, 20), viewerActor);

        processing.processClipNow(clip.id());

        assertEquals(Clip.ProcessingStatus.READY,
                clipRepository.findById(clip.id()).orElseThrow().getProcessingStatus());
    }

    @Test
    void finApresLaDureeRejetee() {
        setUp();
        ApiException ex = assertThrows(ApiException.class,
                () -> clips.createFromVideoSegment(video.getId(), segment(3590, 25), viewerActor));
        assertEquals(400, ex.getStatus().value());
    }

    @Test
    void tropLongRejete() {
        setUp();
        ApiException ex = assertThrows(ApiException.class,
                () -> clips.createFromVideoSegment(video.getId(), segment(0, 61), viewerActor));
        assertEquals(400, ex.getStatus().value());
    }

    @Test
    void tropCourtRejete() {
        setUp();
        ApiException ex = assertThrows(ApiException.class,
                () -> clips.createFromVideoSegment(video.getId(), segment(0, 2), viewerActor));
        assertEquals(400, ex.getStatus().value());
    }

    @Test
    void debutNegatifRejete() {
        setUp();
        ApiException ex = assertThrows(ApiException.class,
                () -> clips.createFromVideoSegment(video.getId(), segment(-5, 10), viewerActor));
        assertEquals(400, ex.getStatus().value());
    }

    @Test
    void sourcePriveeMasquee404() {
        setUp();
        video.changeVisibility(Video.Visibility.PRIVATE);
        videos.saveAndFlush(video);

        ApiException ex = assertThrows(ApiException.class,
                () -> clips.createFromVideoSegment(video.getId(), segment(0, 10), viewerActor));
        assertEquals(404, ex.getStatus().value());
    }

    @Test
    void sourceInexistante404() {
        setUp();
        ApiException ex = assertThrows(ApiException.class,
                () -> clips.createFromVideoSegment(999_999L, segment(0, 10), viewerActor));
        assertEquals(404, ex.getStatus().value());
    }

    @Test
    void nonAuthentifieRefuse() {
        setUp();
        ApiException ex = assertThrows(ApiException.class,
                () -> clips.createFromVideoSegment(video.getId(), segment(0, 10), null));
        assertEquals(403, ex.getStatus().value());
    }

    @Test
    void directSansEnregistrementIndecoupable() {
        setUp();
        Stream live = streams.save(Stream.schedule(channel, "Live", null, Instant.now()));
        live.markStarted();
        streams.saveAndFlush(live);

        ApiException ex = assertThrows(ApiException.class,
                () -> clips.createFromStreamSegment(live.getId(), segment(0, 10), viewerActor));
        assertEquals(400, ex.getStatus().value());
    }

    @Test
    void suppressionLogiqueMasqueLeDetail() {
        setUp();
        ClipDto clip = clips.createFromVideoSegment(video.getId(), segment(0, 10), viewerActor);
        processing.processClipNow(clip.id());

        clips.delete(clip.id(), ownerActor);

        ApiException ex = assertThrows(ApiException.class, () -> clips.getById(clip.id()));
        assertEquals(404, ex.getStatus().value());
        assertTrue(clipRepository.findById(clip.id()).isPresent());
    }

    @Test
    void restaurationProprietaire() {
        setUp();
        ClipDto clip = clips.createFromVideoSegment(video.getId(), segment(0, 10), viewerActor);
        clips.delete(clip.id(), ownerActor);

        ClipDto restored = clips.restore(clip.id(), ownerActor);

        assertEquals(clip.id(), restored.id());
        assertEquals(clip.id(), clips.getById(clip.id()).id());
    }

    @Test
    void visibiliteModerateurIntrusRefuse() {
        setUp();
        ClipDto clip = clips.createFromVideoSegment(video.getId(), segment(0, 10), viewerActor);

        User moderator = users.save(User.create("modoclip15@example.com", "modoclip15", "hash"));
        moderator.changeRole(User.Role.MODERATOR);
        ClipDto privatized = clips.changeVisibility(clip.id(), Clip.Visibility.PRIVATE,
                CurrentUser.of(moderator.getId(), moderator.getEmail(), moderator.getRole()));
        assertEquals(Clip.Visibility.PRIVATE, privatized.visibility());

        User intruder = users.save(User.create("intrusclip15@example.com", "intrusclip15", "hash"));
        intruder.changeRole(User.Role.CREATOR);
        ApiException ex = assertThrows(ApiException.class,
                () -> clips.changeVisibility(clip.id(), Clip.Visibility.PUBLIC,
                        CurrentUser.of(intruder.getId(), intruder.getEmail(), intruder.getRole())));
        assertEquals(403, ex.getStatus().value());
    }
}
