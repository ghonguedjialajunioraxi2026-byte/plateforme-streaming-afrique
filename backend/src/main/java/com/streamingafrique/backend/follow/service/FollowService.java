package com.streamingafrique.backend.follow.service;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.creator.dto.CreatorProfileDto;
import com.streamingafrique.backend.creator.model.CreatorProfile;
import com.streamingafrique.backend.creator.repository.CreatorProfileRepository;
import com.streamingafrique.backend.dto.ApiPageResponse;
import com.streamingafrique.backend.exception.ApiException;
import com.streamingafrique.backend.follow.dto.FollowDto;
import com.streamingafrique.backend.follow.model.Follow;
import com.streamingafrique.backend.follow.repository.FollowRepository;
import com.streamingafrique.backend.notification.model.Notification;
import com.streamingafrique.backend.notification.service.NotificationService;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;

/**
 * Suivis (social, sans argent — les abonnements payants viennent plus tard).
 *
 * Règles métier :
 *   - on ne peut pas se suivre SOI-MÊME ;
 *   - suivre deux fois est une opération IGNORÉE (idempotente) ;
 *   - suivre une chaîne déclenche une notification "nouvel abonné" au
 *     propriétaire de la chaîne (orchestration entre les deux services).
 *
 * Autorisation (étape 6.25) : les écritures portent l'ACTEUR authentifié
 * ({@link CurrentUser}) et exigent {@code followerId == acteur} (requireSelf) —
 * impossible de suivre/se désabonner AU NOM d'un autre utilisateur, même ADMIN
 * (règle psychologique : les abonnements sont personnels).
 * Lectures : publiques.
 */
@Service
public class FollowService {

    private final FollowRepository repository;
    private final UserRepository userRepository;
    private final CreatorProfileRepository creatorRepository;
    private final NotificationService notificationService;

    public FollowService(FollowRepository repository,
                         UserRepository userRepository,
                         CreatorProfileRepository creatorRepository,
                         NotificationService notificationService) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.creatorRepository = creatorRepository;
        this.notificationService = notificationService;
    }

    /** Suit une chaîne (retourne le suivi existant si déjà abonné). */
    @Transactional
    public FollowDto follow(Long followerUserId, Long followedChannelId, CurrentUser actor) {
        CurrentUser.requireSelf(actor, followerUserId);
        User follower = userRepository.findById(followerUserId)
                .orElseThrow(() -> ApiException.notFound("Compte introuvable (id=" + followerUserId + ")"));
        CreatorProfile followed = creatorRepository.findById(followedChannelId)
                .orElseThrow(() -> ApiException.notFound("Chaîne introuvable (id=" + followedChannelId + ")"));

        if (followed.getUser().getId().equals(followerUserId)) {
            throw ApiException.badRequest("On ne peut pas suivre sa propre chaîne");
        }

        Optional<Follow> existing = repository.findByFollowerIdAndFollowedId(followerUserId, followedChannelId);
        if (existing.isPresent()) {
            return toDto(existing.get());
        }

        Follow follow = repository.save(Follow.create(follower, followed));

        // Notification "nouvel abonné" au propriétaire de la chaîne
        notificationService.notify(followed.getUser(),
                Notification.Type.NEW_FOLLOWER,
                "Nouvel abonné",
                follower.getDisplayName() + " suit maintenant votre chaîne",
                "/@" + followed.getSlug());

        return toDto(follow);
    }

    /** Ne plus suivre une chaîne (silencieux si on ne la suivait pas). */
    @Transactional
    public void unfollow(Long followerUserId, Long followedChannelId, CurrentUser actor) {
        CurrentUser.requireSelf(actor, followerUserId);
        Follow follow = repository.findByFollowerIdAndFollowedId(followerUserId, followedChannelId)
                .orElseThrow(() -> ApiException.notFound("Vous ne suivez pas cette chaîne"));
        repository.delete(follow);
    }

    @Transactional(readOnly = true)
    public boolean isFollowing(Long followerUserId, Long followedChannelId) {
        return repository.existsByFollowerIdAndFollowedId(followerUserId, followedChannelId);
    }

    /** Les chaînes suivies par un utilisateur, paginées (fiches publiques). */
    @Transactional(readOnly = true)
    public ApiPageResponse<CreatorProfileDto> getFollowedChannels(Long followerUserId, Pageable pageable) {
        Page<CreatorProfile> page = repository.findFollowedProfilesByFollowerId(followerUserId, pageable);
        return ApiPageResponse.of(
                page.getContent().stream().map(this::toProfileDto).toList(),
                pageable.getPageNumber(), pageable.getPageSize(), page.getTotalElements());
    }

    /** Nombre d'abonnés d'une chaîne. */
    @Transactional(readOnly = true)
    public long countFollowers(Long followedChannelId) {
        return repository.countByFollowedId(followedChannelId);
    }

    /** Nombre de chaînes suivies par un utilisateur. */
    @Transactional(readOnly = true)
    public long countFollowing(Long followerUserId) {
        return repository.countByFollowerId(followerUserId);
    }

    private FollowDto toDto(Follow follow) {
        return new FollowDto(follow.getId(), follow.getFollower().getId(),
                follow.getFollowed().getId(), follow.getCreatedAt());
    }

    private CreatorProfileDto toProfileDto(CreatorProfile profile) {
        CreatorProfileDto.CategoryRef category = profile.getPrimaryCategory() == null ? null
                : new CreatorProfileDto.CategoryRef(profile.getPrimaryCategory().getId(),
                        profile.getPrimaryCategory().getSlug(), profile.getPrimaryCategory().getName());
        return new CreatorProfileDto(profile.getId(), profile.getDisplayName(), profile.getSlug(),
                profile.getBio(), profile.getAvatarUrl(), profile.getBannerUrl(),
                profile.getUser().getId(), profile.getCreatedAt(),
                repository.countByFollowedId(profile.getId()),
                category, profile.getStatus());
    }
}