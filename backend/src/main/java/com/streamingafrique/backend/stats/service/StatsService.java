package com.streamingafrique.backend.stats.service;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.config.RedisCacheProperties;
import com.streamingafrique.backend.redis.CacheKeys;
import com.streamingafrique.backend.redis.RedisCacheService;
import com.streamingafrique.backend.stats.dto.LiveStreamsCountDto;
import com.streamingafrique.backend.stream.model.Stream;
import com.streamingafrique.backend.stream.repository.StreamRepository;

/**
 * Statistiques de la plateforme.
 *
 * Les statistiques « courte durée » (comme le nombre de directs en cours)
 * sont calculées par un comptage en BASE et mises en cache avec un TTL très
 * court ({@code statsTtl} = 20 s par défaut). La base reste la SEULE source
 * de vérité : le cache ne sert qu'à éviter de recompter la même chose à
 * chaque affichage du banner « N directs en cours ».
 *
 * Cohérence complète : en plus du TTL court, {@link StreamService} invalide
 * explicitement la clé aux transitions start/end — le chiffre ne peut donc
 * jamais rester périmé plus de quelques secondes.
 */
@Service
public class StatsService {

    private final StreamRepository streamRepository;
    private final RedisCacheService cache;
    private final Duration statsTtl;

    public StatsService(StreamRepository streamRepository,
                        RedisCacheService cache,
                        RedisCacheProperties redisProperties) {
        this.streamRepository = streamRepository;
        this.cache = cache;
        this.statsTtl = redisProperties.getStatsTtl();
    }

    /** Nombre de directs en cours — cache-aside à TTL court. */
    @Transactional(readOnly = true)
    public LiveStreamsCountDto getLiveStreamsCount() {
        String key = CacheKeys.liveStreamsCount();

        Object cached = cache.getJson(key);
        if (cached instanceof LiveStreamsCountDto dto) {
            return dto;
        }

        LiveStreamsCountDto dto = new LiveStreamsCountDto(
                streamRepository.countByStatus(Stream.Status.LIVE), Instant.now());
        cache.putJson(key, dto, statsTtl);
        return dto;
    }
}