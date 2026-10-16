package com.streamingafrique.backend.auth.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.auth.PasswordPolicy;
import com.streamingafrique.backend.auth.TokenHash;
import com.streamingafrique.backend.auth.dto.AuthTokens;
import com.streamingafrique.backend.auth.dto.ChangePasswordRequest;
import com.streamingafrique.backend.auth.dto.LoginRequest;
import com.streamingafrique.backend.auth.dto.LoginResponse;
import com.streamingafrique.backend.auth.dto.MeResponse;
import com.streamingafrique.backend.auth.dto.RegisterRequest;
import com.streamingafrique.backend.auth.dto.ResetPasswordRequest;
import com.streamingafrique.backend.auth.dto.SessionDto;
import com.streamingafrique.backend.auth.mail.MailService;
import com.streamingafrique.backend.auth.model.AuthSession;
import com.streamingafrique.backend.auth.model.EmailToken;
import com.streamingafrique.backend.auth.repository.AuthSessionRepository;
import com.streamingafrique.backend.auth.repository.EmailTokenRepository;
import com.streamingafrique.backend.beta.service.BetaService;
import com.streamingafrique.backend.exception.ApiException;
import com.streamingafrique.backend.profile.model.UserProfile;
import com.streamingafrique.backend.profile.repository.UserProfileRepository;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.security.jwt.JwtService;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;

/**
 * Authentification complète (étapes 6.24 + 08).
 *
 * Flux couverts, TOUS avec réponses génériques anti-énumération sauf conflit
 * d'inscription (409 attendu : l'utilisateur VIENT de fournir ces données) :
 *   - register : validation → unicités → BCrypt → User (rôle USER forcé) +
 *     UserProfile → token de vérification + email (dev : log) ;
 *   - login : AuthenticationManager (email + mot de passe, compte désactivé
 *     rejeté en 401 générique) → recordLogin → paire access + refresh
 *     (session persistée, hash seul) ;
 *   - refresh : JWT REFRESH valide + session active en base → ROTATION
 *     (ancienne révoquée, nouvelle émise) ;
 *   - logout : révocation idempotente (succès même si déjà révoqué/inconnu) ;
 *   - verify-email / resend : token 24 h à usage unique ;
 *   - forgot/reset : réponse générique TOUJOURS, token 1 h à usage unique,
 *     TOUTES sessions révoquées au reset ;
 *   - change-password : mot de passe actuel vérifié, TOUTES sessions révoquées ;
 *   - sessions : liste + révocation unitaire (propriété vérifiée).
 *
 * Secrets : BCrypt pour les mots de passe, SHA-256 (empreinte) pour les
 * tokens persistés, SecureRandom 256 bits pour les jetons email. Aucun
 * secret en log, aucun mot de passe/hash/token dans les réponses.
 */
@Service
public class AuthService {

    /** Validité du lien de vérification email. */
    private static final Duration VERIFY_TTL = Duration.ofHours(24);

    /** Validité du lien de récupération. */
    private static final Duration RESET_TTL = Duration.ofHours(1);

    /** Purge des sessions expirées depuis plus de ce délai (au refresh). */
    private static final Duration SESSION_PURGE_AFTER = Duration.ofDays(30);

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final UserProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthSessionRepository sessionRepository;
    private final EmailTokenRepository emailTokenRepository;
    private final MailService mailService;
    private final String frontendUrl;
    private final BetaService betaService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(AuthenticationManager authenticationManager,
                       JwtService jwtService,
                       UserRepository userRepository,
                       UserProfileRepository profileRepository,
                       PasswordEncoder passwordEncoder,
                       AuthSessionRepository sessionRepository,
                       EmailTokenRepository emailTokenRepository,
                       MailService mailService,
                       BetaService betaService,
                       @Value("${FRONTEND_URL:http://localhost:5173}") String frontendUrl) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionRepository = sessionRepository;
        this.emailTokenRepository = emailTokenRepository;
        this.mailService = mailService;
        this.betaService = betaService;
        this.frontendUrl = frontendUrl;
    }

    /* ─── Inscription ─────────────────────────────────────────── */

    @Transactional
    public MeResponse register(RegisterRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw ApiException.badRequest("Le mot de passe et sa confirmation diffèrent");
        }
        PasswordPolicy.check(request.password());
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("Un compte existe déjà avec cet email");
        }
        if (userRepository.existsByUsername(username)) {
            throw ApiException.conflict("Ce nom d'utilisateur est déjà pris");
        }
        // Rôle FORCÉ côté serveur : le DTO n'a aucun champ role.
        User user = User.create(email, username, request.username().trim(), passwordEncoder.encode(request.password()));
        // Consentement CGU : la case obligatoire du formulaire vaut acceptation.
        user.acceptTerms();
        userRepository.save(user);
        profileRepository.save(UserProfile.create(user));
        // Bêta privée (étape 24) : en mode invite-only, l'inscription exige
        // un code valide lié à cet email — échec = rollback complet
        // (aucun compte orphelin, même transaction).
        if (betaService.isInviteOnly()) {
            betaService.consumeInvitation(email, request.inviteCode(), user.getId());
        }
        issueEmailToken(user, EmailToken.Type.VERIFY_EMAIL, VERIFY_TTL);
        return MeResponse.of(user);
    }

    /* ─── Connexion ───────────────────────────────────────────── */

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        // 1. Authentification : BadCredentials/Disabled → 401 générique (GlobalExceptionHandler).
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password()));

        // 2. Compte + dernière connexion + paire de jetons (session persistée).
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> ApiException.notFound("Compte introuvable : " + email));
        return issueLogin(user, email, null, null);
    }

    /**
     * Émet une session FLICK complète pour un compte DÉJÀ authentifié
     * (mot de passe vérifié OU id_token Google vérifié — connexion Google).
     * Enregistre la connexion, persiste la session refresh et remet la paire
     * access + refresh : Google ne devient JAMAIS le système de session,
     * seul le JWT FLICK sert les routes protégées.
     *
     * @param email email NORMALISÉ par l'appelant (identifiant de connexion,
     *              subject du jeton) — le service ne relit jamais
     *              {@code user.getEmail()} ici (invariant testé)
     */
    @Transactional
    public LoginResponse issueLogin(User user, String email, String deviceInfo, String ipAddress) {
        user.recordLogin();
        userRepository.save(user);
        AuthTokens tokens = issueSession(user, email, deviceInfo, ipAddress);
        return new LoginResponse(tokens.accessToken(), tokens.tokenType(), tokens.expiresIn(),
                user.getId(), email, user.getDisplayName(), user.getRole(),
                tokens.refreshToken(), tokens.refreshExpiresIn());
    }

    /* ─── Refresh (rotation) / logout ─────────────────────────── */

    @Transactional
    public AuthTokens refresh(String refreshToken, String deviceInfo, String ipAddress) {
        if (!jwtService.isValidRefreshToken(refreshToken)) {
            throw ApiException.badRequest("Refresh token invalide ou expiré");
        }
        AuthSession session = sessionRepository.findByTokenHash(TokenHash.sha256Hex(refreshToken))
                .filter(AuthSession::isActive)
                .orElseThrow(() -> ApiException.badRequest("Refresh token invalide ou expiré"));
        User user = session.getUser();
        if (user.getStatus() != User.Status.ACTIVE) {
            session.revoke();
            throw ApiException.badRequest("Refresh token invalide ou expiré");
        }
        // Rotation : l'ancien est révoqué AVANT d'émettre le nouveau.
        session.revoke();
        sessionRepository.save(session);
        sessionRepository.deleteByExpiresAtBefore(Instant.now().minus(SESSION_PURGE_AFTER));
        sessionRepository.flush();
        AuthTokens tokens = issueSession(user, user.getEmail(), deviceInfo, ipAddress);
        return tokens;
    }

    /** Révocation idempotente : succès même si le token est inconnu/déjà révoqué. */
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        sessionRepository.findByTokenHash(TokenHash.sha256Hex(refreshToken.trim()))
                .ifPresent(session -> {
                    session.revoke();
                    sessionRepository.save(session);
                });
    }

    /* ─── Compte courant / sessions ───────────────────────────── */

    @Transactional(readOnly = true)
    public MeResponse me(CurrentUser actor) {
        return MeResponse.of(findActiveOrThrow(actor));
    }

    @Transactional(readOnly = true)
    public List<SessionDto> sessions(CurrentUser actor) {
        findActiveOrThrow(actor);
        return sessionRepository.findByUserIdAndRevokedAtIsNullOrderByLastUsedAtDesc(actor.id())
                .stream().map(SessionDto::of).toList();
    }

    /** Révocation unitaire : 404 si inexistante OU appartenant à autrui (anti-énumération). */
    @Transactional
    public void revokeSession(CurrentUser actor, Long sessionId) {
        AuthSession session = sessionRepository.findById(sessionId)
                .filter(s -> s.getUser().getId().equals(actor.id()))
                .orElseThrow(() -> ApiException.notFound("Session introuvable"));
        session.revoke();
        sessionRepository.save(session);
    }

    /* ─── Vérification email ──────────────────────────────────── */

    @Transactional
    public void verifyEmail(String rawToken) {
        EmailToken token = emailTokenRepository.findByTokenHash(TokenHash.sha256Hex(rawToken.trim()))
                .filter(t -> t.getType() == EmailToken.Type.VERIFY_EMAIL)
                .filter(EmailToken::isUsable)
                .orElseThrow(() -> ApiException.badRequest("Lien de vérification invalide ou expiré"));
        token.markUsed();
        emailTokenRepository.save(token);
        User user = token.getUser();
        user.markEmailVerified();
        userRepository.save(user);
    }

    /** TOUJOURS succès générique (anti-énumération) ; anciens jetons invalidés. */
    @Transactional
    public void resendVerification(String email) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        userRepository.findByEmail(normalized)
                .filter(user -> !user.isEmailVerified())
                .ifPresent(user -> {
                    invalidateEmailTokens(user.getId(), EmailToken.Type.VERIFY_EMAIL);
                    issueEmailToken(user, EmailToken.Type.VERIFY_EMAIL, VERIFY_TTL);
                });
    }

    /* ─── Mot de passe oublié / réinitialisation / changement ─── */

    /** TOUJOURS succès générique (anti-énumération), compte existant ou non. */
    @Transactional
    public void forgotPassword(String email) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        userRepository.findByEmail(normalized).ifPresent(user -> {
            invalidateEmailTokens(user.getId(), EmailToken.Type.PASSWORD_RESET);
            issueEmailToken(user, EmailToken.Type.PASSWORD_RESET, RESET_TTL);
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw ApiException.badRequest("Le mot de passe et sa confirmation diffèrent");
        }
        PasswordPolicy.check(request.newPassword());
        EmailToken token = emailTokenRepository
                .findByTokenHash(TokenHash.sha256Hex(request.token().trim()))
                .filter(t -> t.getType() == EmailToken.Type.PASSWORD_RESET)
                .filter(EmailToken::isUsable)
                .orElseThrow(() -> ApiException.badRequest("Lien de récupération invalide ou expiré"));
        token.markUsed();
        emailTokenRepository.save(token);
        User user = token.getUser();
        userRepository.save(user);
        setPassword(user, request.newPassword());
        revokeAllSessions(user.getId());
    }

    @Transactional
    public void changePassword(CurrentUser actor, ChangePasswordRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw ApiException.badRequest("Le mot de passe et sa confirmation diffèrent");
        }
        User user = findActiveOrThrow(actor);
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("Le mot de passe actuel est incorrect");
        }
        PasswordPolicy.check(request.newPassword());
        setPassword(user, request.newPassword());
        // Sécurité : toutes les sessions tombent (autres appareils déconnectés,
        // le client courant se reconnecte — retour explicite au frontend).
        revokeAllSessions(user.getId());
    }

    /* ─── Internes ────────────────────────────────────────────── */

    private User findActiveOrThrow(CurrentUser actor) {
        CurrentUser.requireAuthenticated(actor);
        return userRepository.findById(actor.id())
                .filter(user -> user.getStatus() == User.Status.ACTIVE)
                .orElseThrow(() -> ApiException.forbidden("Compte suspendu ou fermé"));
    }

    private void setPassword(User user, String rawPassword) {
        // Hachage BCrypt systématique : jamais de clair en base, jamais en réponse.
        user.changePasswordHash(passwordEncoder.encode(rawPassword));
        userRepository.save(user);
    }

    private AuthTokens issueSession(User user, String email, String deviceInfo, String ipAddress) {
        String roleAuthority = "ROLE_" + user.getRole().name();
        String accessToken = jwtService.generateAccessToken(user.getId(), email, roleAuthority);
        String refreshToken = jwtService.generateRefreshToken(user.getId(), email, roleAuthority);
        Instant expiresAt = Instant.now().plusSeconds(jwtService.refreshTokenTtlSeconds());
        sessionRepository.save(AuthSession.open(user, TokenHash.sha256Hex(refreshToken),
                truncate(deviceInfo), truncate(ipAddress), expiresAt));
        return new AuthTokens(accessToken, "Bearer", jwtService.accessTokenTtlSeconds(),
                refreshToken, jwtService.refreshTokenTtlSeconds());
    }

    private void issueEmailToken(User user, EmailToken.Type type, Duration ttl) {
        byte[] random = new byte[32];
        secureRandom.nextBytes(random);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        emailTokenRepository.save(EmailToken.issue(user, TokenHash.sha256Hex(raw), type,
                Instant.now().plus(ttl)));
        String link = frontendUrl + (type == EmailToken.Type.VERIFY_EMAIL
                ? "/verification-email?token=" + raw
                : "/reinitialisation?token=" + raw);
        if (type == EmailToken.Type.VERIFY_EMAIL) {
            mailService.sendVerificationEmail(user.getEmail(), link);
        } else {
            mailService.sendPasswordResetEmail(user.getEmail(), link);
        }
    }

    private void invalidateEmailTokens(Long userId, EmailToken.Type type) {
        emailTokenRepository.findByUserIdAndType(userId, type).forEach(token -> {
            token.markUsed();
            emailTokenRepository.save(token);
        });
    }

    private void revokeAllSessions(Long userId) {
        sessionRepository.findByUserId(userId).forEach(session -> {
            session.revoke();
            sessionRepository.save(session);
        });
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > 200 ? trimmed.substring(0, 200) : trimmed;
    }
}
