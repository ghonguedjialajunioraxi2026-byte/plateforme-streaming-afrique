package com.streamingafrique.backend.video;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.category.model.Category;
import com.streamingafrique.backend.category.repository.CategoryRepository;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sous-étape 7.14 : table videos — origine stream, traitement, visibilité,
 * suppression logique, références objet/CDN (H2, transaction annulée).
 * Aucun blob en base, aucun compteur de vues stocké (volontaire).
 */
@SpringBootTest
@Transactional
class VideoStructureTest {

    @Autowired
    private UserRepository users;
    @Autowired
    private CreatorProfileRepository channels;
    @Autowired
    private CategoryRepository categories;
    @Autowired
    private StreamRepository streams;
    @Autowired
    private VideoRepository videos;

    private CreatorProfile channel(String email, String slug) {
        User user = users.save(User.create(email, slug, "hash"));
        return channels.save(CreatorProfile.create(user, slug + " TV", slug));
    }

    @Test
    void publication_directe_defauts_disponible_publique() {
        CreatorProfile channel = channel("vod1@example.com", "vod1");
        Category tech = categories.save(Category.create("Tech", "tech", "Tech"));

        Video video = videos.save(Video.publish(channel, "Tuto", "Desc", tech,
                "https://cdn.example.com/v.mp4", "https://cdn.example.com/v.jpg", 600));

        assertEquals(Video.Visibility.PUBLIC, video.getVisibility());
        assertEquals(Video.ProcessingStatus.READY, video.getProcessingStatus());
        assertFalse(video.isDeleted());
        assertNull(video.getOriginStream());
        assertNull(video.getStorageKey());
        assertNotNull(video.getPublishedAt());
    }

    @Test
    void replay_issu_d_un_stream_avec_origine_tracee() {
        CreatorProfile channel = channel("vod2@example.com", "vod2");
        Stream live = streams.save(Stream.schedule(channel, "Live", null, Instant.now()));
        live.markStarted();
        streams.saveAndFlush(live);

        Video replay = videos.save(Video.fromStream(channel, live, "Replay",
                "Rediffusion", "https://cdn.example.com/r.mp4", 3600));

        assertEquals(live.getId(), videos.findById(replay.getId()).orElseThrow()
                .getOriginStream().getId());
    }

    @Test
    void cycle_traitement_visibilite_suppression_et_source_objet() {
        CreatorProfile channel = channel("vod3@example.com", "vod3");
        Video video = videos.save(Video.publish(channel, "Brouillon", null, null,
                "https://cdn.example.com/b.mp4", null, 120));

        video.changeProcessingStatus(Video.ProcessingStatus.PROCESSING);
        video.changeProcessingStatus(Video.ProcessingStatus.READY);
        video.changeVisibility(Video.Visibility.PRIVATE);
        video.attachSource("videos/1/source.mp4", "https://cdn.example.com/signed/b.mp4?exp=3600");
        video.markDeleted();
        videos.saveAndFlush(video);

        Video lu = videos.findById(video.getId()).orElseThrow();
        assertEquals(Video.ProcessingStatus.READY, lu.getProcessingStatus());
        assertEquals(Video.Visibility.PRIVATE, lu.getVisibility());
        assertEquals("videos/1/source.mp4", lu.getStorageKey());
        assertTrue(lu.isDeleted());
    }
}
