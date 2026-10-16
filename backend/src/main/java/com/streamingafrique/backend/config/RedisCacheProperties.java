package com.streamingafrique.backend.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Paramètres applicatifs Redis (distincts de la connexion technique gérée par
 * {@code spring.data.redis.*}).
 *
 *  - {@code prefix}     : préfixe de toutes les clés ("sa" par défaut).
 *                         Sépare les espaces de noms si Redis est partagé.
 *  - {@code defaultTtl} : durée de vie par défaut d'une entrée cache.
 *  - {@code *Ttl}       : TTL spécifique par type de donnée mise en cache.
 *                         La base de données RESTE la source de vérité :
 *                         le cache accélère les lectures, jamais l'écrit.
 *
 * Surcharges par variables d'environnement : REDIS_PREFIX, REDIS_DEFAULT_TTL,
 * REDIS_CATEGORIES_TTL, REDIS_CREATORS_TTL, REDIS_STATS_TTL, REDIS_LISTS_TTL.
 */
@ConfigurationProperties(prefix = "app.redis")
public class RedisCacheProperties {

    private String prefix = "sa";

    private Duration defaultTtl = Duration.ofMinutes(10);

    /** TTL des listes de catégories publiques (peu changeantes, très consultées). */
    private Duration categoriesTtl = Duration.ofMinutes(15);

    /** TTL des fiches publiques de créateurs. */
    private Duration creatorsTtl = Duration.ofMinutes(5);

    /** TTL des statistiques à courte durée de vie (ex. nb de directs actifs). */
    private Duration statsTtl = Duration.ofSeconds(20);

    /** TTL général des listes publiques très consultées (lives, vidéos…). */
    private Duration listsTtl = Duration.ofMinutes(5);

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public Duration getDefaultTtl() {
        return defaultTtl;
    }

    public void setDefaultTtl(Duration defaultTtl) {
        this.defaultTtl = defaultTtl;
    }

    public Duration getCategoriesTtl() {
        return categoriesTtl;
    }

    public void setCategoriesTtl(Duration categoriesTtl) {
        this.categoriesTtl = categoriesTtl;
    }

    public Duration getCreatorsTtl() {
        return creatorsTtl;
    }

    public void setCreatorsTtl(Duration creatorsTtl) {
        this.creatorsTtl = creatorsTtl;
    }

    public Duration getStatsTtl() {
        return statsTtl;
    }

    public void setStatsTtl(Duration statsTtl) {
        this.statsTtl = statsTtl;
    }

    public Duration getListsTtl() {
        return listsTtl;
    }

    public void setListsTtl(Duration listsTtl) {
        this.listsTtl = listsTtl;
    }
}
