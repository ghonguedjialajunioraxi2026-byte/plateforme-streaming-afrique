package com.streamingafrique.backend.creator.dashboard.dto;

import java.time.Instant;
import java.util.List;

/**
 * Communauté de la chaîne (étape 16 : {@code GET /api/creator/community}).
 *
 * Followers (suivi gratuit) et abonnés (payants) restent deux populations
 * distinctes — jamais fusionnées. Seuls des pseudos/avatars publics sortent
 * (jamais d'email).
 */
public record CreatorCommunityDto(
        long followersCount,
        long subscribersCount,
        List<MemberInfo> recentFollowers,
        List<SubscriberInfo> recentSubscribers
) {
    /** Un follower récent (profil public uniquement). */
    public record MemberInfo(
            Long userId,
            String username,
            String displayName,
            String avatarUrl,
            Instant followedAt
    ) {
    }

    /** Un abonné payant récent (sans montant : voir /revenue). */
    public record SubscriberInfo(
            Long userId,
            String displayName,
            String planName,
            Instant since
    ) {
    }
}
