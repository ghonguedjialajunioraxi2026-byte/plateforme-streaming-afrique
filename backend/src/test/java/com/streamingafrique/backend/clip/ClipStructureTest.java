package com.streamingafrique.backend.clip;

import java.time.Instant;

import org.hibernate.annotations.Check;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.clip.model.Clip;
import com.streamingafrique.backend.clip.repository.ClipRepository;
import com.streamingafrique.backend.creator.model.CreatorProfile;
import com.streamingafrique.backend.creator.repository.CreatorProfileRepository;
import com.streamingafrique.backend.stream.model.Stream;
import com.streamingafrique.backend.stream.repository.StreamRepository;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;
import com.streamingafrique.backend.video.model.Video;
import com.streamingafrique.backend.video.repository.VideoRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sous-étape 7.15 : table clips — auteur chaîne, source XOR, statut,
 * visibilité, suppression logique, partage public (H2, transaction annulée).
 * Aucun blob en base, aucun compteur de vues stocké (volontaire).
 */
@SpringBootTest
@Transactional
class ClipStructureTest {

    @Autowired
    private UserRepository users;
    @Autowired
    private CreatorProfileRepository channels;
    @Autowired
    private StreamRepository streams;
    @Autowired
    private VideoRepository videos;
    @Autowired
    private ClipRepository clips;

    private CreatorProfile channel(String email, String slug) {
        User user = users.save(User.create(email, slug, "hash"));
        return channels.save(CreatorProfile.create(user, slug + " TV", slug));
    }

    @Test
    void clip_stream_defauts_public_pret_avec_partage() {
        CreatorProfile channel = channel("clip1@example.com", "clip1");
        Stream live = streams.save(Stream.schedule(channel, "Live", null, Instant.now()));

        Clip clip = clips.save(Clip.fromStream(channel, live, "Top", "https://cdn.example.com/c.mp4", 30));

        assertEquals(channel.getId(), clip.getCreator().getId());
        assertEquals(live.getId(), clip.getStream().getId());
        assertNull(clip.getVideo());
        assertEquals(Clip.Visibility.PUBLIC, clip.getVisibility());
        assertEquals(Clip.ProcessingStatus.READY, clip.getProcessingStatus());
        assertFalse(clip.isDeleted());
        assertNotNull(clip.getShareSlug());
    }

    @Test
    void clip_video_source_video_seule() {
        CreatorProfile channel = channel("clip2@example.com", "clip2");
        Video video = videos.save(Video.publish(channel, "Tuto", null, null,
                "https://cdn.example.com/v.mp4", null, 600));

        Clip clip = clips.save(Clip.fromVideo(channel, video, "Extrait",
                "https://cdn.example.com/c.mp4", 45));

        assertNull(clip.getStream());
        assertEquals(video.getId(), clip.getVideo().getId());
    }

    @Test
    void contrainte_xor_exclusive_en_base() {
        // Définition : exactement une source (ni zéro, ni deux).
        Check check = Clip.class.getAnnotation(Check.class);
        assertNotNull(check);
        assertTrue(check.constraints().contains("<>"),
                "le CHECK doit être un XOR, or : " + check.constraints());

        // Zéro source → refus base (fabrique sans source).
        CreatorProfile channel = channel("clip3@example.com", "clip3");
        assertThrows(DataIntegrityViolationException.class, () ->
                clips.saveAndFlush(Clip.fromStream(channel, null, "Orphelin",
                        "https://cdn.example.com/o.mp4", 10)));
    }

    @Test
    void cycle_statut_visibilite_segment_source_partage_suppression() {
        CreatorProfile channel = channel("clip4@example.com", "clip4");
        Stream live = streams.save(Stream.schedule(channel, "Live", null, Instant.now()));
        Clip clip = clips.save(Clip.fromStream(channel, live, "Moment",
                "https://cdn.example.com/m.mp4", 20));
        String ancienPartage = clip.getShareSlug();

        clip.changeProcessingStatus(Clip.ProcessingStatus.PROCESSING);
        clip.changeProcessingStatus(Clip.ProcessingStatus.READY);
        clip.changeVisibility(Clip.Visibility.PRIVATE);
        clip.updateSegment(95);
        clip.attachSource("clips/1/source.mp4", "https://cdn.example.com/signed/m.mp4?exp=3600");
        clip.rotateShareSlug();
        clip.markDeleted();
        clips.saveAndFlush(clip);

        Clip lu = clips.findById(clip.getId()).orElseThrow();
        assertEquals(Clip.ProcessingStatus.READY, lu.getProcessingStatus());
        assertEquals(Clip.Visibility.PRIVATE, lu.getVisibility());
        assertEquals(95, lu.getStartsAtSeconds());
        assertEquals("clips/1/source.mp4", lu.getStorageKey());
        assertNotEquals(ancienPartage, lu.getShareSlug());
        assertTrue(lu.isDeleted());
    }
}
