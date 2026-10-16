package com.streamingafrique.backend.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Vérifie la connexion Redis au démarrage de l'application.
 *
 * Le résultat est loggé (INFO si OK, WARN si indisponible) sans interrompre
 * le démarrage : l'application fonctionne en mode dégradé si Redis est absent.
 */
@Component
public class RedisConnectionProbe implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RedisConnectionProbe.class);

    private final RedisCacheService cacheService;

    public RedisConnectionProbe(RedisCacheService cacheService) {
        this.cacheService = cacheService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (cacheService.ping()) {
            log.info("Redis connecté (host={}, port={})",
                    System.getenv().getOrDefault("REDIS_HOST", "localhost"),
                    System.getenv().getOrDefault("REDIS_PORT", "6379"));
        } else {
            log.warn("Redis indisponible — les fonctionnalités Redis seront indisponibles");
        }
    }
}
