package com.streamingafrique.backend.stream;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.category.model.Category;
import com.streamingafrique.backend.category.repository.CategoryRepository;
import com.streamingafrique.backend.creator.model.CreatorProfile;
import com.streamingafrique.backend.creator.repository.CreatorProfileRepository;
import com.streamingafrique.backend.stream.model.Stream;
import com.streamingafrique.backend.stream.repository.StreamRepository;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sous-étape 7.13 : table streams — métier vs technique, cycle de vie,
 * pic d'audience monotone, masquage, retrouvailles des lives actifs (H2,
 * transaction annulée après chaque test).
 */
@SpringBootTest
@Transactional
class StreamStructureTest {

    @Autowired
    private UserRepository users;
    @Autowired
    private CreatorProfileRepository channels;
    @Autowired
    private CategoryRepository categories;
    @Autowired
    private StreamRepository streams;

    private CreatorProfile channel(String email, String slug) {
        User user = users.save(User.create(email, slug, "hash"));
        return channels.save(CreatorProfile.create(user, slug + " TV", slug));
    }

    @Test
    void programmation_defauts_metier_sans_technique() {
        CreatorProfile channel = channel("live1@example.com", "live1");
        Category jeux = categories.save(Category.create("Jeux", "jeux", "Gaming"));

        Stream stream = streams.save(Stream.schedule(channel, "Tournoi", jeux, Instant.now()));

        assertEquals(Stream.Status.SCHEDULED, stream.getStatus());
        assertEquals(Stream.Visibility.PUBLIC, stream.getVisibility());
        assertEquals(0, stream.getPeakViewers());
        assertFalse(stream.isDeleted());
        assertNull(stream.getStreamKeyHash());
        assertNull(stream.getIngestProtocol());
        assertNull(stream.getPlaybackUrl());
    }

    @Test
    void cycle_vie_metadonnees_pic_monotone_et_masquage() {
        CreatorProfile channel = channel("live2@example.com", "live2");
        Stream stream = streams.save(Stream.schedule(channel, "Talk", null, Instant.now()));

        stream.updateMetadata("Débat du soir", "https://cdn.example.com/t.jpg", "fr", Stream.Visibility.UNLISTED);
        stream.markStarted();
        stream.recordPeakViewers(120);
        stream.recordPeakViewers(80);
        stream.recordPeakViewers(300);
        streams.saveAndFlush(stream);

        Stream lu = streams.findById(stream.getId()).orElseThrow();
        assertEquals(Stream.Status.LIVE, lu.getStatus());
        assertEquals("Débat du soir", lu.getDescription());
        assertEquals("fr", lu.getLanguage());
        assertEquals(Stream.Visibility.UNLISTED, lu.getVisibility());
        assertEquals(300, lu.getPeakViewers());

        lu.markEnded();
        lu.markDeleted();
        streams.saveAndFlush(lu);
        assertEquals(Stream.Status.ENDED, streams.findById(stream.getId()).orElseThrow().getStatus());
        assertTrue(streams.findById(stream.getId()).orElseThrow().isDeleted());
    }

    @Test
    void references_techniques_rattachees_sans_clair() {
        CreatorProfile channel = channel("live3@example.com", "live3");
        Stream stream = streams.save(Stream.schedule(channel, "Concert", null, Instant.now()));

        stream.attachIngest("sha256:empreinte-future", Stream.IngestProtocol.RTMP,
                "https://media.example.com/live3/index.m3u8");
        streams.saveAndFlush(stream);

        Stream lu = streams.findById(stream.getId()).orElseThrow();
        assertEquals("sha256:empreinte-future", lu.getStreamKeyHash());
        assertEquals(Stream.IngestProtocol.RTMP, lu.getIngestProtocol());
    }

    @Test
    void lives_actifs_retrouves_rapidement_par_statut() {
        CreatorProfile channel = channel("live4@example.com", "live4");
        Stream live = streams.save(Stream.schedule(channel, "Direct", null, Instant.now()));
        live.markStarted();
        streams.saveAndFlush(live);
        streams.save(Stream.schedule(channel, "Demain", null, Instant.now().plusSeconds(86400)));

        var page = streams.findByStatus(Stream.Status.LIVE, PageRequest.of(0, 10));
        assertTrue(page.getContent().stream().anyMatch(s -> s.getId().equals(live.getId())));
        assertTrue(streams.countByStatus(Stream.Status.LIVE) >= 1);
    }
}
