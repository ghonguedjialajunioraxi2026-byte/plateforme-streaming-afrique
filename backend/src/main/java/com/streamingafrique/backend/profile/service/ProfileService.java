package com.streamingafrique.backend.profile.service;

import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.streamingafrique.backend.exception.ApiException;
import com.streamingafrique.backend.media.dto.UploadedImageDto;
import com.streamingafrique.backend.media.model.ImageKind;
import com.streamingafrique.backend.media.service.UploadService;
import com.streamingafrique.backend.media.storage.ImageStorage;
import com.streamingafrique.backend.profile.dto.ProfileDto;
import com.streamingafrique.backend.profile.dto.PublicProfileDto;
import com.streamingafrique.backend.profile.dto.UpdateProfileRequest;
import com.streamingafrique.backend.profile.model.UserProfile;
import com.streamingafrique.backend.profile.repository.UserProfileRepository;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;

/**
 * Profil utilisateur — TOUTE la logique métier vit ici (étape 09).
 *
 * Séparation compte/profil : l'identité d'authentification (email, username,
 * rôle, statut) ne transite JAMAIS ici ; ce service ne lit/écrit que
 * l'affichage (displayName/avatarUrl sur {@code users}) et l'extension
 * ({@code profiles}). Le contrôleur ne fait qu'acheminer + {@code CurrentUser}.
 *
 * Profil manquant (comptes legacy) : création paresseuse à la première
 * lecture/écriture du propriétaire — jamais de 404 pour son propre profil.
 * En lecture PUBLIQUE : profil absent ou non public → 404 (compte privé).
 */
@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final UserProfileRepository profileRepository;
    private final UploadService uploadService;
    private final ImageStorage imageStorage;

    public ProfileService(UserRepository userRepository, UserProfileRepository profileRepository,
                          UploadService uploadService, ImageStorage imageStorage) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.uploadService = uploadService;
        this.imageStorage = imageStorage;
    }

    /** Profil COMPLET du connecté (lazy-create si compte legacy). */
    @Transactional
    public ProfileDto getMyProfile(CurrentUser actor) {
        CurrentUser.requireAuthenticated(actor);
        User user = findActiveOrThrow(actor.id());
        UserProfile profile = getOrCreate(user);
        return toDto(user, profile);
    }

    /** Profil PUBLIC par username (liste blanche stricte, jamais d'email/secret). */
    @Transactional(readOnly = true)
    public PublicProfileDto getPublicProfile(String username) {
        User user = userRepository.findByUsername(username.trim())
                .orElseThrow(() -> ApiException.notFound("Profil introuvable : " + username));
        UserProfile profile = profileRepository.findByUserId(user.getId())
                .filter(UserProfile::isPublic)
                .orElseThrow(() -> ApiException.notFound("Profil introuvable : " + username));
        return new PublicProfileDto(user.getUsername(), user.getDisplayName(), user.getAvatarUrl(),
                profile.getBio(), profile.getCountryCode(), profile.getLanguage(),
                profile.getWebsiteUrl(), profile.getXHandle(), profile.getInstagramHandle(),
                profile.getYoutubeUrl(), user.getCreatedAt());
    }

    /** Modification du profil connecté (champs présents uniquement). */
    @Transactional
    public ProfileDto updateMyProfile(CurrentUser actor, UpdateProfileRequest request) {
        CurrentUser.requireAuthenticated(actor);
        User user = findActiveOrThrow(actor.id());
        UserProfile profile = getOrCreate(user);

        if (request.displayName() != null) {
            String displayName = request.displayName().trim();
            if (displayName.isEmpty()) {
                throw ApiException.badRequest("Le nom d'affichage ne peut pas être vide");
            }
            user.updatePublicInfo(displayName, user.getAvatarUrl());
        }
        // PUT partiel : null = inchangé (jamais d'effacement implicite).
        profile.updatePresentation(
                request.bio() != null ? request.bio() : profile.getBio(),
                request.countryCode() != null ? normalizeCountry(request.countryCode()) : profile.getCountryCode(),
                request.language() != null ? normalizeLanguage(request.language()) : profile.getLanguage());
        profile.updateLinks(
                request.websiteUrl() != null
                        ? requireHttpUrl(request.websiteUrl(), "site") : profile.getWebsiteUrl(),
                request.xHandle() != null ? request.xHandle() : profile.getXHandle(),
                request.instagramHandle() != null ? request.instagramHandle() : profile.getInstagramHandle(),
                request.youtubeUrl() != null
                        ? requireHttpUrl(request.youtubeUrl(), "YouTube") : profile.getYoutubeUrl());
        if (request.birthDate() != null) {
            profile.updateBirthDate(request.birthDate());
        }
        if (request.timezone() != null || request.preferredCurrency() != null) {
            profile.updateRegionPrefs(
                    request.timezone() != null ? normalizeTimezone(request.timezone())
                            : profile.getTimezone(),
                    request.preferredCurrency() != null
                            ? normalizeCurrency(request.preferredCurrency())
                            : profile.getPreferredCurrency());
        }
        if (request.notifyLive() != null || request.notifyContent() != null || request.isPublic() != null) {
            profile.updatePreferences(
                    request.notifyLive() != null ? request.notifyLive() : profile.isNotifyLive(),
                    request.notifyContent() != null ? request.notifyContent() : profile.isNotifyContent(),
                    request.isPublic() != null ? request.isPublic() : profile.isPublic());
        }
        userRepository.save(user);
        profileRepository.save(profile);
        return toDto(user, profile);
    }

    /**
     * Avatar du connecté (étape 09, §8-10) : upload sécurisé
     * ({@code UploadService} : MIME sniffé, taille, nom UUID) + liaison
     * {@code User.avatarUrl} + suppression de l'ancien fichier S'IL vit dans
     * notre stockage (jamais une URL externe).
     */
    @Transactional
    public ProfileDto updateAvatar(CurrentUser actor, MultipartFile file) {
        CurrentUser.requireAuthenticated(actor);
        User user = findActiveOrThrow(actor.id());
        UploadedImageDto uploaded = uploadService.uploadImage(ImageKind.AVATAR, file);
        String oldUrl = user.getAvatarUrl();
        user.updatePublicInfo(user.getDisplayName(), uploaded.url());
        userRepository.save(user);
        deleteStoredFile(oldUrl);
        return toDto(user, getOrCreate(user));
    }

    /* ─── Internes ────────────────────────────────────────────── */

    private User findActiveOrThrow(Long userId) {
        return userRepository.findById(userId)
                .filter(user -> user.getStatus() == User.Status.ACTIVE)
                .orElseThrow(() -> ApiException.forbidden("Compte suspendu ou fermé"));
    }

    private UserProfile getOrCreate(User user) {
        return profileRepository.findByUserId(user.getId())
                .orElseGet(() -> profileRepository.save(UserProfile.create(user)));
    }

    private static String normalizeCountry(String countryCode) {
        if (countryCode == null) {
            return null;
        }
        return countryCode.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeLanguage(String language) {
        if (language == null) {
            return null;
        }
        return language.trim();
    }

    /** Fuseau IANA valide (ZoneId) — "UTC" accepté, valeur inconnue refusée. */
    private static String normalizeTimezone(String timezone) {
        String trimmed = timezone.trim();
        try {
            java.time.ZoneId.of(trimmed);
        } catch (Exception ex) {
            throw ApiException.badRequest("Fuseau horaire inconnu : " + trimmed);
        }
        return trimmed;
    }

    /** Devise ISO-3 normalisée (l'existence du pays/devise se règle côté région). */
    private static String normalizeCurrency(String currency) {
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    /** URL http(s) exigée si le champ est renseigné (anti javascript:/data:). */
    private static String requireHttpUrl(String url, String field) {
        if (url == null) {
            return null;
        }
        String trimmed = url.trim();
        if (!(trimmed.startsWith("http://") || trimmed.startsWith("https://"))) {
            throw ApiException.badRequest("L'URL du champ " + field + " doit commencer par http(s)://");
        }
        return trimmed;
    }

    /** Supprime l'ancien fichier S'IL vit dans notre stockage (jamais une URL externe). */
    private void deleteStoredFile(String url) {
        if (url == null) {
            return;
        }
        String trimmed = url.trim();
        if (!trimmed.startsWith("/uploads/")) {
            return;
        }
        String relative = trimmed.substring("/uploads/".length());
        if (!relative.isBlank() && !relative.contains("..")) {
            imageStorage.delete(relative);
        }
    }

    private static ProfileDto toDto(User user, UserProfile profile) {
        return new ProfileDto(user.getId(), user.getUsername(), user.getEmail(),
                user.getDisplayName(), user.getAvatarUrl(), user.isEmailVerified(),
                profile.getBio(), profile.getCountryCode(), profile.getLanguage(),
                profile.getTimezone(), profile.getPreferredCurrency(),
                profile.getWebsiteUrl(), profile.getXHandle(), profile.getInstagramHandle(),
                profile.getYoutubeUrl(), profile.getBirthDate(), profile.isNotifyLive(),
                profile.isNotifyContent(), profile.isPublic(), user.getCreatedAt());
    }
}
