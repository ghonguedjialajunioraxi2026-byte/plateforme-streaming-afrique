package com.streamingafrique.backend.stream.service;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.creator.model.CreatorProfile;
import com.streamingafrique.backend.creator.repository.CreatorProfileRepository;
import com.streamingafrique.backend.exception.ApiException;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.stream.dto.IngestInfoDto;
import com.streamingafrique.backend.stream.dto.StreamKeyResponse;
import com.streamingafrique.backend.stream.model.Stream;
import com.streamingafrique.backend.stream.repository.StreamRepository;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FINALISATION : émission de stream key — clé montrée une fois, seule
 * l'empreinte persiste, rotation invalide l'ancienne, propriétaire ou ADMIN.
 */
@SpringBootTest
@Transactional
class StreamKeyIssuanceTest {

    @Autowired
    private StreamService service;
    @Autowired
    private StreamKeyService keys;
    @Autowired
    private UserRepository users;
    @Autowired
    private CreatorProfileRepository channels;
    @Autowired
    private StreamRepository streams;

    private CreatorProfile channel(String slug) {
        User owner = users.save(User.create(slug + "@example.com", slug, slug + " TV", "hash"));
        return channels.save(CreatorProfile.create(owner, slug + " TV", slug));
    }

    private CurrentUser actor(User user) {
        return CurrentUser.of(user.getId(), user.getEmail(), User.Role.CREATOR);
    }

    @Test
    void emission_stocke_empreinte_et_expose_une_fois() {
        CreatorProfile channel = channel("keylive1");
        Stream stream = streams.save(Stream.schedule(channel, "Live", null, Instant.now()));

        StreamKeyResponse response = service.issueStreamKey(stream.getId(), actor(channel.getUser()));

        assertNotNull(response.streamKey());
        assertTrue(response.streamKey().startsWith(StreamKeyService.KEY_PREFIX));
        assertEquals(stream.getId() + "?key=" + response.streamKey(), response.obsStreamKey());
        assertNotNull(response.rtmpUrl());

        Stream saved = streams.findById(stream.getId()).orElseThrow();
        assertNotNull(saved.getStreamKeyHash());
        assertTrue(keys.matches(response.streamKey(), saved.getStreamKeyHash()));
        assertNotNull(saved.getPlaybackUrl());
        assertEquals(Stream.IngestProtocol.RTMP, saved.getIngestProtocol());

        IngestInfoDto info = service.ingestInfo(stream.getId(), actor(channel.getUser()));
        assertTrue(info.hasKey());
        assertEquals(response.rtmpUrl(), info.rtmpUrl());
    }

    @Test
    void rotation_invalide_ancienne_cle() {
        CreatorProfile channel = channel("keylive2");
        Stream stream = streams.save(Stream.schedule(channel, "Live", null, Instant.now()));

        StreamKeyResponse first = service.issueStreamKey(stream.getId(), actor(channel.getUser()));
        StreamKeyResponse second = service.issueStreamKey(stream.getId(), actor(channel.getUser()));

        Stream saved = streams.findById(stream.getId()).orElseThrow();
        assertFalse(keys.matches(first.streamKey(), saved.getStreamKeyHash()));
        assertTrue(keys.matches(second.streamKey(), saved.getStreamKeyHash()));
    }

    @Test
    void tiers_refuse_proprietaire_ou_admin_exige() {
        CreatorProfile channel = channel("keylive3");
        Stream stream = streams.save(Stream.schedule(channel, "Live", null, Instant.now()));
        User stranger = users.save(User.create("stranger@example.com", "stranger", "Stranger", "hash"));

        assertThrows(ApiException.class,
                () -> service.issueStreamKey(stream.getId(), actor(stranger)));
        assertThrows(ApiException.class,
                () -> service.ingestInfo(stream.getId(), actor(stranger)));
    }
}
