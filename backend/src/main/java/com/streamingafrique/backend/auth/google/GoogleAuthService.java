package com.streamingafrique.backend.auth.google;

import java.util.Locale;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.auth.dto.LoginResponse;
import com.streamingafrique.backend.auth.dto.MeResponse;
import com.streamingafrique.backend.auth.service.AuthService;
import com.streamingafrique.backend.exception.ApiException;
import com.streamingafrique.backend.profile.model.UserProfile;
import com.streamingafrique.backend.profile.repository.UserProfileRepository;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;

/**
 * Connexion "Continuer avec Google" (flux ID token OIDC).
 *
 * Chaîne de confiance : Google Identity Services → id_token → backend
 * (vérifié : signature, iss, aud, exp) → compte FLICK → JWT FLICK.
 * Le frontend n'est JAMAIS cru sur parole : un simple email envoyé par
 * React ne prouve rien, seul l'id_token VÉRIFIÉ fait foi.
 *
 * Cas couverts :
 *   - CAS 1 (nouveau) : création automatique du compte FLICK (rôle USER,
 *     sans mot de passe, email vérifié si Google le certifie) + profil ;
 *   - CAS 2 (google_id connu) : profil rafraîchi (nom/photo), JWT remis ;
 *   - CAS 3 (email LOCAL existant) : 409 CONFLICT, JAMAIS de doublon ni de
 *     liaison automatique — l'utilisateur se connecte avec son mot de
 *     passe puis lie explicitement son compte (POST /api/auth/google/link) ;
 *   - CAS 4/5 (refus/erreurs Google) : traités côté frontend (pas de
 *     credential → pas d'appel) et ici en 401 générique (token invalide,
 *     expiré, falsifié) ; jamais de pile ni de secret en réponse.
 *
 * Comptes suspendus/fermés : rejetés comme au login classique (403).
 */
@Service
public class GoogleAuthService {

    private final GoogleIdTokenValidator validator;
    private final AuthService authService;
    private final UserRepository userRepository;
    private final UserProfileRepository profileRepository;

    public GoogleAuthService(GoogleIdTokenValidator validator,
                             AuthService authService,
                             UserRepository userRepository,
                             UserProfileRepository profileRepository) {
        this.validator = validator;
        this.authService = authService;
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
    }

    /* ─── Connexion / inscription via Google ──────────────────── */

    @Transactional
    public LoginResponse loginWithGoogle(String idToken, String deviceInfo, String ipAddress) {
        GoogleProfile profile = validator.verify(idToken);
        String email = profile.email().trim().toLowerCase(Locale.ROOT);

        // CAS 2 — compte Google déjà connu : profil rafraîchi + JWT.
        User user = userRepository.findByGoogleId(profile.googleId()).orElse(null);
        if (user != null) {
            requireActive(user);
            user.updateGoogleProfile(displayNameOf(profile), profile.pictureUrl());
            if (profile.emailVerified()) {
                user.markEmailVerified();
            }
            userRepository.save(user);
            return authService.issueLogin(user, email, deviceInfo, ipAddress);
        }

        // CAS 3 — email déjà pris par un compte LOCAL : pas de doublon, pas
        // de fusion automatique (un email identique ne prouve pas l'identité).
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("Un compte existe déjà avec cet email. "
                    + "Connectez-vous avec votre mot de passe, puis liez votre compte Google "
                    + "depuis les paramètres.");
        }

        // CAS 1 — premier passage : création automatique (rôle USER forcé).
        String username = uniqueUsername(email);
        User created = User.createGoogle(email, username, displayNameOf(profile),
                profile.googleId(), profile.pictureUrl(), profile.emailVerified());
        userRepository.save(created);
        profileRepository.save(UserProfile.create(created));
        return authService.issueLogin(created, email, deviceInfo, ipAddress);
    }

    /* ─── Liaison explicite (connecté + mot de passe déjà vérifié) ─ */

    /**
     * Lie le compte Google (id_token vérifié) au compte FLICK CONNECTÉ.
     * Prérequis : l'appelant est authentifié (JWT FLICK valide) — c'est
     * cette session existante qui prouve l'identité, pas l'email Google.
     * 409 si ce compte Google est déjà lié à UN AUTRE compte FLICK.
     */
    @Transactional
    public MeResponse linkGoogle(CurrentUser actor, String idToken) {
        CurrentUser.requireAuthenticated(actor);
        GoogleProfile profile = validator.verify(idToken);
        User user = userRepository.findById(actor.id())
                .filter(u -> u.getStatus() == User.Status.ACTIVE)
                .orElseThrow(() -> ApiException.forbidden("Compte suspendu ou fermé"));
        userRepository.findByGoogleId(profile.googleId())
                .filter(other -> !Objects.equals(other.getId(), user.getId()))
                .ifPresent(other -> {
                    throw ApiException.conflict("Ce compte Google est déjà lié à un autre compte FLICK");
                });
        user.linkGoogle(profile.googleId());
        if (profile.emailVerified()) {
            user.markEmailVerified();
        }
        userRepository.save(user);
        return MeResponse.of(user);
    }

    /* ─── Internes ────────────────────────────────────────────── */

    private static void requireActive(User user) {
        if (user.getStatus() != User.Status.ACTIVE) {
            throw ApiException.forbidden("Compte suspendu ou fermé");
        }
    }

    /** Nom affiché : nom complet Google, sinon prénom + nom, sinon email. */
    private static String displayNameOf(GoogleProfile profile) {
        if (profile.fullName() != null && !profile.fullName().isBlank()) {
            return profile.fullName().trim();
        }
        String given = profile.givenName() == null ? "" : profile.givenName().trim();
        String family = profile.familyName() == null ? "" : profile.familyName().trim();
        String joined = (given + " " + family).trim();
        return joined.isEmpty() ? profile.email() : joined;
    }

    /**
     * Pseudonyme unique dérivé de l'email (même racine que le LOCAL).
     * Collision (compte LOCAL homonyme…) → suffixe numérique croissant.
     * Boucle bornée : au-delà de 10 000 essais, 409 explicite.
     */
    private String uniqueUsername(String email) {
        String base = User.deriveUsername(email);
        if (!userRepository.existsByUsername(base)) {
            return base;
        }
        for (int i = 1; i <= 10_000; i++) {
            String candidate = base.length() > 44 ? base.substring(0, 44) + i : base + i;
            if (!userRepository.existsByUsername(candidate)) {
                return candidate;
            }
        }
        throw ApiException.conflict("Impossible de générer un nom d'utilisateur, réessayez");
    }
}
