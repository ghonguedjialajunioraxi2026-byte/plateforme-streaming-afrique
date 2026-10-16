package com.streamingafrique.backend.video;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.clip.model.Clip;
import com.streamingafrique.backend.clip.repository.ClipRepository;
import com.streamingafrique.backend.creator.model.CreatorProfile;
import com.streamingafrique.backend.creator.repository.CreatorProfileRepository;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;
import com.streamingafrique.backend.video.model.Video;
import com.streamingafrique.backend.video.repository.VideoRepository;
import com.streamingafrique.backend.video.service.MediaProcessingRecovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Étape 15 — rejeu au boot (H2, transaction annulée).
 *
 * Couvre : orphelins PROCESSING (VOD + clip) retraités en READY en un
 * appel, supprimés ignorés, appel sans orphelin = 0.
 */
@SpringBootTest
@Transactional
class MediaProcessingRecoveryTest {

    @Autowired
    private MediaProcessingRecovery recovery;
    @Autowired
    private VideoRepository videos;
    @Autowired
    private ClipRepository clips;
    @Autowired
    private UserRepository users;
    @Autowired
    private CreatorProfileRepository channels;

    private CreatorProfile channel;

    private void setUp() {
        User owner = users.save(User.create("recovery15@example.com", "recovery15", "hash"));
        channel = channels.save(CreatorProfile.create(owner, "Recovery TV", "recovery15"));
    }

    @Test
    void orphelinsRetraitesEnUnAppel() {
        setUp();
        Video vod = videos.save(Video.publish(channel, "VOD", null, null,
                "https://cdn.example.com/v.mp4", null, 100));
        vod.changeProcessingStatus(Video.ProcessingStatus.PROCESSING);
        videos.save(vod);
        Clip clip = clips.save(Clip.fromVideo(channel,
                videos.save(Video.publish(channel, "Source", null, null,
                        "https://cdn.example.com/s.mp4", null, 600)),
                "Extrait", "https://cdn.example.com/c.mp4", 10));
        clip.changeProcessingStatus(Clip.ProcessingStatus.PROCESSING);
        clips.save(clip);

        assertEquals(2, recovery.recover());

        assertEquals(Video.ProcessingStatus.READY,
                videos.findById(vod.getId()).orElseThrow().getProcessingStatus());
        assertEquals(Clip.ProcessingStatus.READY,
                clips.findById(clip.getId()).orElseThrow().getProcessingStatus());
    }

    @Test
    void supprimesIgnoresEtVideVautZero() {
        setUp();
        Video gone = videos.save(Video.publish(channel, "Masquée", null, null,
                "https://cdn.example.com/g.mp4", null, 100));
        gone.changeProcessingStatus(Video.ProcessingStatus.PROCESSING);
        gone.markDeleted();
        videos.save(gone);

        assertEquals(0, recovery.recover());
        assertTrue(videos.findById(gone.getId()).orElseThrow().isDeleted());
    }
}
