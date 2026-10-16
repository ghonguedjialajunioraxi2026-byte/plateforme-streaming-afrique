package com.streamingafrique.backend.video;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.clip.dto.ClipDto;
import com.streamingafrique.backend.clip.dto.CreateClipSegmentRequest;
import com.streamingafrique.backend.clip.service.ClipService;
import com.streamingafrique.backend.creator.model.CreatorProfile;
import com.streamingafrique.backend.creator.repository.CreatorProfileRepository;
import com.streamingafrique.backend.exception.ApiException;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;
import com.streamingafrique.backend.video.model.Video;
import com.streamingafrique.backend.video.repository.VideoRepository;
import com.streamingafrique.backend.video.service.VideoService;
import com.streamingafrique.backend.video.service.ViewerKeys;

import jakarta.servlet.http.HttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Étape 15 — vues anti-abus (H2 + Redis local, transactions annulées).
 *
 * Couvre : première vue comptée, rejeu même viewer ignoré (refresh ×1000
 * ≠ 1000 vues), viewer distinct compté, privée/en traitement = 404.
 */
@SpringBootTest
@Transactional
class ViewCountingTest {

    @Autowired
    private VideoService videos;
    @Autowired
    private ClipService clips;
    @Autowired
    private VideoRepository videoRepository;
    @Autowired
    private UserRepository users;
    @Autowired
    private CreatorProfileRepository channels;
    @Autowired
    private com.streamingafrique.backend.redis.RedisCacheService cache;

    private CreatorProfile channel;
    private Video video;

    /**
     * Herméticité : Redis est PARTAGÉ entre les runs (TTL 1 h) alors que les
     * IDs H2 recommencent — on purge les gardes de vues avant chaque test.
     */
    @BeforeEach
    void purgeViewGuards() {
        cache.evictByPattern("views:*");
    }

    private void setUp() {
        User owner = users.save(User.create("vues15@example.com", "vues15", "hash"));
        owner.changeRole(User.Role.CREATOR);
        channel = channels.save(CreatorProfile.create(owner, "Vues TV", "vues15"));
        video = videoRepository.save(Video.publish(channel, "VOD", "Desc", null,
                "https://cdn.example.com/v.mp4", null, 600));
    }

    private static HttpServletRequest requestFrom(String ip) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn(ip);
        return request;
    }

    /**
     * IP unique par appel (Redis est PARTAGÉ entre les runs Maven avec un TTL
     * d'une heure, alors que les IDs H2 recommencent à 1 : une clé fixe
     * serait déjà prise par un run précédent — herméticité des tests).
     */
    private static int ipCounter = 0;

    private static synchronized HttpServletRequest uniqueRequest() {
        return requestFrom("10.9." + (ipCounter / 250) + "." + (1 + (ipCounter++ % 250)));
    }

    @Test
    void premiereVueCompteeRejeuIgnore() {
        setUp();
        String key = ViewerKeys.of(uniqueRequest(), null);

        assertEquals(1, videos.recordView(video.getId(), key));
        assertEquals(1, videos.recordView(video.getId(), key));
        assertEquals(1, videos.recordView(video.getId(), key));
        assertEquals(1, videoRepository.findById(video.getId()).orElseThrow().getViewCount());
    }

    @Test
    void viewerDistinctCompteConnecteCompte() {
        setUp();
        videos.recordView(video.getId(), ViewerKeys.of(uniqueRequest(), null));

        User fan = users.save(User.create("fan15@example.com", "fan15", "hash"));
        CurrentUser actor = CurrentUser.of(fan.getId(), fan.getEmail(), fan.getRole());
        long count = videos.recordView(video.getId(), ViewerKeys.of(uniqueRequest(), actor));

        assertEquals(2, count);
    }

    @Test
    void ipJamaisExposeeEnClair() {
        String key = ViewerKeys.of(requestFrom("192.168.1.10"), null);

        assertEquals("ip:", key.substring(0, 3));
        assertFalse(key.contains("192.168.1.10"));
    }

    @Test
    void vuePrivee404() {
        setUp();
        video.changeVisibility(Video.Visibility.PRIVATE);
        videoRepository.saveAndFlush(video);

        ApiException ex = assertThrows(ApiException.class,
                () -> videos.recordView(video.getId(), ViewerKeys.of(requestFrom("10.0.0.3"), null)));
        assertEquals(404, ex.getStatus().value());
    }

    @Test
    void clipVueCompteeUneFois() {
        setUp();
        ClipDto clip = clips.createFromVideo(channel.getId(), video.getId(),
                new com.streamingafrique.backend.clip.dto.ClipRequest(
                        "Extrait", "https://cdn.example.com/c.mp4", null, 10),
                CurrentUser.of(channel.getUser().getId(), channel.getUser().getEmail(),
                        User.Role.CREATOR));
        String key = ViewerKeys.of(uniqueRequest(), null);

        assertEquals(1, clips.recordView(clip.id(), key));
        assertEquals(1, clips.recordView(clip.id(), key));
    }
}
