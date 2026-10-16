package com.streamingafrique.backend.beta.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.beta.config.BetaProperties;
import com.streamingafrique.backend.beta.dto.BetaDtos.BetaFeedbackDto;
import com.streamingafrique.backend.beta.dto.BetaDtos.BetaInvitationDto;
import com.streamingafrique.backend.beta.dto.BetaDtos.InvitationCreatedResponse;
import com.streamingafrique.backend.beta.dto.BetaDtos.SubmitFeedbackRequest;
import com.streamingafrique.backend.beta.model.BetaFeedback;
import com.streamingafrique.backend.beta.model.BetaInvitation;
import com.streamingafrique.backend.beta.repository.BetaFeedbackRepository;
import com.streamingafrique.backend.beta.repository.BetaInvitationRepository;
import com.streamingafrique.backend.dto.ApiPageResponse;
import com.streamingafrique.backend.exception.ApiException;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;

/**
 * Bêta privée (étape 24) — invitations + feedback, UN seul mécanisme.
 *
 * Invitations : code aléatoire (SecureRandom, 192 bits, lisible), stocké
 * en empreinte SHA-256 — montré en clair UNE fois à la création. Lié à
 * UN email (incessible), usage unique, expiration. Vérifié à
 * l'inscription quand {@code app.beta.invite-only} est actif.
 *
 * Feedback : tout connecté peut signaler (rapporteur = soi, forcé
 * serveur) ; triage (statut + priorité P0..P3) réservé ADMIN.
 */
@Service
public class BetaService {

    private final SecureRandom random = new SecureRandom();

    private final BetaInvitationRepository invitations;
    private final BetaFeedbackRepository feedbacks;
    private final UserRepository users;
    private final BetaProperties properties;

    public BetaService(BetaInvitationRepository invitations,
                       BetaFeedbackRepository feedbacks,
                       UserRepository users,
                       BetaProperties properties) {
        this.invitations = invitations;
        this.feedbacks = feedbacks;
        this.users = users;
        this.properties = properties;
    }

    /* ─── Invitations (ADMIN) ─────────────────────────────────── */

    /** Crée une invitation et renvoie le code EN CLAIR (unique affichage). */
    @Transactional
    public InvitationCreatedResponse invite(String email, String note, CurrentUser actor) {
        CurrentUser.requireAuthenticated(actor);
        if (!actor.isAdmin()) {
            throw ApiException.forbidden("Seul un ADMIN peut inviter à la bêta");
        }
        String normalized = email.trim().toLowerCase(java.util.Locale.ROOT);
        String code = generateCode();
        Instant expiresAt = Instant.now().plus(properties.getInvitationTtl());
        BetaInvitation invitation = BetaInvitation.invite(normalized, sha256Hex(code),
                expiresAt, actor.id(), note == null ? null : note.trim());
        invitations.save(invitation);
        return new InvitationCreatedResponse(invitation.getId(), normalized, code, expiresAt);
    }

    @Transactional(readOnly = true)
    public ApiPageResponse<BetaInvitationDto> listInvitations(BetaInvitation.Status status,
                                                              Pageable pageable, CurrentUser actor) {
        CurrentUser.requireAuthenticated(actor);
        if (!actor.isAdmin()) {
            throw ApiException.forbidden("Seul un ADMIN peut lister les invitations");
        }
        var page = status == null
                ? invitations.findAll(pageable)
                : invitations.findByStatusOrderByCreatedAtDesc(status, pageable);
        return ApiPageResponse.of(page.getContent().stream().map(BetaInvitationDto::of).toList(),
                pageable.getPageNumber(), pageable.getPageSize(), page.getTotalElements());
    }

    /** Révoque une invitation inutilisée (auditée par l'appelant). */
    @Transactional
    public BetaInvitationDto revoke(Long id, CurrentUser actor) {
        CurrentUser.requireAuthenticated(actor);
        if (!actor.isAdmin()) {
            throw ApiException.forbidden("Seul un ADMIN peut révoquer une invitation");
        }
        BetaInvitation invitation = invitations.findById(id)
                .orElseThrow(() -> ApiException.notFound("Invitation introuvable (id=" + id + ")"));
        if (invitation.getStatus() != BetaInvitation.Status.PENDING) {
            throw ApiException.conflict("Invitation déjà utilisée ou révoquée");
        }
        invitation.revoke();
        return BetaInvitationDto.of(invitations.save(invitation));
    }

    /* ─── Vérification à l'inscription ────────────────────────── */

    /**
     * Valide et consomme l'invitation pour cet email (appelée par
     * {@code AuthService.register} quand le mode invite-only est actif).
     * 403 si absente/invalide/expirée/utilisée ou liée à un autre email.
     */
    @Transactional
    public void consumeInvitation(String email, String code, Long acceptedUserId) {
        if (code == null || code.isBlank()) {
            throw ApiException.forbidden("Inscription sur invitation uniquement (code requis)");
        }
        BetaInvitation invitation = invitations.findByCodeHash(sha256Hex(code.trim()))
                .orElseThrow(() -> ApiException.forbidden("Code d'invitation invalide"));
        if (!invitation.isUsable()) {
            throw ApiException.forbidden("Invitation expirée, révoquée ou déjà utilisée");
        }
        if (!invitation.getEmail().equalsIgnoreCase(email.trim())) {
            throw ApiException.forbidden("Ce code est lié à un autre email");
        }
        invitation.markAccepted(acceptedUserId);
        invitations.save(invitation);
    }

    public boolean isInviteOnly() {
        return properties.isInviteOnly();
    }

    /* ─── Feedback ────────────────────────────────────────────── */

    /** Signale un retour (rapporteur = acteur authentifié, forcé serveur). */
    @Transactional
    public BetaFeedbackDto submit(SubmitFeedbackRequest request, CurrentUser actor) {
        CurrentUser.requireAuthenticated(actor);
        User reporter = users.findById(actor.id())
                .orElseThrow(() -> ApiException.notFound("Compte introuvable"));
        BetaFeedback feedback = BetaFeedback.submit(reporter, request.category(),
                request.title().trim(), request.description().trim(),
                blankToNull(request.route()), blankToNull(request.appVersion()),
                blankToNull(request.device()));
        return BetaFeedbackDto.of(feedbacks.save(feedback));
    }

    @Transactional(readOnly = true)
    public ApiPageResponse<BetaFeedbackDto> myFeedback(Pageable pageable, CurrentUser actor) {
        CurrentUser.requireAuthenticated(actor);
        var page = feedbacks.findByReporterIdOrderByCreatedAtDesc(actor.id(), pageable);
        return ApiPageResponse.of(page.getContent().stream().map(BetaFeedbackDto::of).toList(),
                pageable.getPageNumber(), pageable.getPageSize(), page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ApiPageResponse<BetaFeedbackDto> listFeedback(BetaFeedback.Status status,
                                                         Pageable pageable, CurrentUser actor) {
        CurrentUser.requireAuthenticated(actor);
        if (!actor.isAdmin()) {
            throw ApiException.forbidden("Seul un ADMIN peut lister les retours");
        }
        var page = status == null
                ? feedbacks.findAll(pageable)
                : feedbacks.findByStatusOrderByCreatedAtDesc(status, pageable);
        return ApiPageResponse.of(page.getContent().stream().map(BetaFeedbackDto::of).toList(),
                pageable.getPageNumber(), pageable.getPageSize(), page.getTotalElements());
    }

    /** Triage ADMIN : statut + priorité P0..P3 (auditée par l'appelant). */
    @Transactional
    public BetaFeedbackDto triage(Long id, BetaFeedback.Status status,
                                  BetaFeedback.Priority priority, CurrentUser actor) {
        CurrentUser.requireAuthenticated(actor);
        if (!actor.isAdmin()) {
            throw ApiException.forbidden("Seul un ADMIN peut trier les retours");
        }
        BetaFeedback feedback = feedbacks.findById(id)
                .orElseThrow(() -> ApiException.notFound("Retour introuvable (id=" + id + ")"));
        feedback.triage(status, priority);
        return BetaFeedbackDto.of(feedbacks.save(feedback));
    }

    /* ─── Internes ────────────────────────────────────────────── */

    /** Code lisible : préfixe + 192 bits d'entropie (SecureRandom). */
    private String generateCode() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return "FLICK-BETA-" + HexFormat.of().formatHex(bytes).substring(0, 16).toUpperCase();
    }

    static String sha256Hex(String input) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible sur cette JVM", e);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Compteurs honnêtes (issues de la base, jamais inventés) pour /admin/beta. */
    @Transactional(readOnly = true)
    public BetaStats stats(CurrentUser actor) {
        CurrentUser.requireAuthenticated(actor);
        if (!actor.isAdmin()) {
            throw ApiException.forbidden("Seul un ADMIN peut voir les statistiques bêta");
        }
        List<BetaInvitation> all = invitations.findAll();
        long pending = all.stream().filter(i -> i.getStatus() == BetaInvitation.Status.PENDING).count();
        long accepted = all.stream().filter(i -> i.getStatus() == BetaInvitation.Status.ACCEPTED).count();
        return new BetaStats(all.size(), pending, accepted,
                feedbacks.count(),
                feedbacks.findByStatusOrderByCreatedAtDesc(BetaFeedback.Status.OPEN,
                        org.springframework.data.domain.PageRequest.of(0, 1)).getTotalElements());
    }

    public record BetaStats(long invitations, long pending, long accepted,
                            long feedback, long feedbackOpen) {
    }
}
