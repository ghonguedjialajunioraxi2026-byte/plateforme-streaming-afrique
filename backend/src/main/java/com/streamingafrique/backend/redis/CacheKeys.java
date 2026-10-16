package com.streamingafrique.backend.redis;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.StringJoiner;

/**
 * Schéma de clés Redis pour le cache.
 *
 * Chaque zone de cache a un espace de noms cohérent :
 *   categories:list:{page}:{size}:{sort}   → liste publique paginée
 *   categories:{id}                        → catégorie par identifiant
 *   creators:public:{id}                   → fiche créateur (infos publiques)
 *   creators:slug:{slug}                   → fiche créateur par slug
 *   stats:streams:live                     → nombre de directs en cours
 *
 * Le préfixe applicatif (ex. "sa:") est ajouté par {@link RedisCacheService}
 * : les clés ici sont les parties domaines uniquement.
 *
 * Lorsqu'on écrase des listes paginées, on invalide par pattern
 * (ex. "categories:list:*") au lieu de supprimer chaque page individuelle.
 */
public final class CacheKeys {

    private CacheKeys() {
    }

    /* ─── Catégories ─────────────────────────────────────────── */
    public static final String CATEGORY_LIST_PREFIX = "categories:list:";

    public static String category(Long id) {
        return "categories:" + id;
    }

    /**
     * Clé de la liste publique paginée (page 0, taille 10, tri par défaut).
     * Les paramètres de pagination sont encodés dans la clé pour éviter
     * qu'une requête avec un tri différent ne renvoie un résultat périmé.
     */
    public static String categoriesList(Pageable pageable) {
        return pageList(CATEGORY_LIST_PREFIX, pageable);
    }

    /* ─── Listes très consultées (feeds publics, lives) ──────── */
    public static final String VIDEO_FEED_PREFIX = "videos:feed:";
    public static final String CLIP_FEED_PREFIX = "clips:feed:";
    public static final String STREAM_LIST_PREFIX = "streams:list:";

    /** Feed public des vidéos (page 0, taille 12, plus récentes d'abord). */
    public static String videoFeed(Pageable pageable) {
        return pageList(VIDEO_FEED_PREFIX, pageable);
    }

    /** Feed public des clips. */
    public static String clipFeed(Pageable pageable) {
        return pageList(CLIP_FEED_PREFIX, pageable);
    }

    /**
     * Liste des directs paginée, discriminée par statut
     * ({@code "ALL"} pour toutes, {@code "live"} pour la page "en direct").
     */
    public static String streamsList(String status, Pageable pageable) {
        return pageList(STREAM_LIST_PREFIX, status, pageable);
    }

    /* ─── Créateurs ──────────────────────────────────────────── */
    /**
     * Fiches créateur v2 (étape 09 : DTO enrichi followers/catégorie/statut).
     * Le suffixe invalide proprement l'ancien format sérialisé en cache.
     */
    public static String creator(Long id) {
        return "creators:public:v2:" + id;
    }

    public static String creatorBySlug(String slug) {
        return "creators:slug:v2:" + slug;
    }

    /* ─── Statistiques (durée courte) ────────────────────────── */
    public static String liveStreamsCount() {
        return "stats:streams:live";
    }

    /* ─── Spectateurs live (étape 10) ──────────────────────────
     * HLS = fichiers statiques : le serveur média ne voit pas les viewers.
     * Le frontend pulse (heartbeat ~30s) ; chaque jeton alimente un
     * HyperLogLog (comptage approximatif, mémoire constante) avec TTL
     * rafraîchi à chaque pulsation. PostgreSQL ne reçoit que le pic.
     */
    /** HyperLogLog des jetons de visionnage d'un direct. */
    public static String streamViewers(Long streamId) {
        return "streams:viewers:" + streamId;
    }

    /** Pic de spectateurs observé (miroir Redis, flush DB à la fin du live). */
    public static String streamViewersPeak(Long streamId) {
        return "streams:viewers-peak:" + streamId;
    }

    /* ─── Présence utilisateur ───────────────────────────────── */
    public static final String PRESENCE_USER_PREFIX = "presence:user:";
    public static final String PRESENCE_ONLINE_SET = "presence:online";

    /** Marqueur de présence d'un utilisateur (JSON + TTL, jamais définitif). */
    public static String presenceUser(Long userId) {
        return PRESENCE_USER_PREFIX + userId;
    }

    /* ─── Utilitaires ────────────────────────────────────────── */

    /** Clé de liste paginée : préfixe + page + taille + tri. */
    private static String pageList(String prefix, Pageable pageable) {
        return prefix
                + pageable.getPageNumber()
                + ":" + pageable.getPageSize()
                + ":" + sortKey(pageable);
    }

    /** Clé de liste paginée avec discriminants (statut, filtre…) : préfixe + discriminant + page + taille + tri. */
    private static String pageList(String prefix, String discriminator, Pageable pageable) {
        return prefix
                + discriminator
                + ":" + pageable.getPageNumber()
                + ":" + pageable.getPageSize()
                + ":" + sortKey(pageable);
    }

    /**
     * Encode le tri de manière déterministe pour servir de suffixe de clé.
     * {@code sort=name,asc → "name:ASC"}, tri par défaut → "DEFAULT".
     */
    private static String sortKey(Pageable pageable) {
        Sort sort = pageable.getSort();
        if (!sort.isSorted()) {
            return "DEFAULT";
        }
        StringJoiner joiner = new StringJoiner(",");
        for (Sort.Order order : sort) {
            joiner.add(order.getProperty() + ":" + order.getDirection());
        }
        return joiner.toString();
    }
}
