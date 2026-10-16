package com.streamingafrique.backend.user.service;

import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.dto.ApiPageResponse;
import com.streamingafrique.backend.exception.ApiException;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.user.dto.UserDto;
import com.streamingafrique.backend.user.dto.UserRequest;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;

/**
 * Comptes utilisateurs — TOUTE la logique métier du compte vit ici :
 * unicité d'email (normalisée en minuscules), promotion de rôle, conversion
 * entité → DTO. Le controller ne fait qu'acheminer.
 *
 * Sécurité (socle 6.23) : si la requête d'inscription fournit un mot de passe
 * (champ optionnel), il est haché BCrypt et stocké dans {@code passwordHash} —
 * jamais en clair. Sans mot de passe, le compte reste public (cas legacy).
 *
 * Autorisation (étape 6.25, durcie 22 / SEC-002) : les actions sensibles prennent l'ACTEUR courant
 * ({@link com.streamingafrique.backend.security.CurrentUser}) en paramètre et
 * vérifient la PROPRIÉTÉ + le RÔLE côté métier — principe de moindre confiance :
 * il ne suffit JAMAIS d'être authentifié, ni de connaître l'id cible.
 *  - getById/getAll → lectures publiques MAIS l'email est MASQUÉ sauf pour le
 *    propriétaire du compte ou un ADMIN (donnée personnelle, anti-énumération) ;
 *  - update → propriétaire du compte OU ADMIN ;
 *  - delete → ADMIN uniquement ;
 *  - changeRole → ADMIN uniquement, et interdiction totale de s'attribuer
 *    (ou se retirer) ADMIN À SOI-MÊME : le dernier admin ne s'auto-dégrade pas.
 */
@Service
public class UserService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserDto create(UserRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (repository.existsByEmail(email)) {
            throw ApiException.conflict("Un compte existe déjà avec cet email : " + email);
        }
        String passwordHash = request.password() != null
                ? passwordEncoder.encode(request.password())
                : null;
        User user = User.create(email, request.displayName().trim(), passwordHash);
        return toDto(repository.save(user));
    }

    @Transactional(readOnly = true)
    public UserDto getById(Long id, CurrentUser actor) {
        return toDto(findByIdOrThrow(id), emailVisibleTo(id, actor));
    }

    @Transactional(readOnly = true)
    public ApiPageResponse<UserDto> getAll(Pageable pageable, CurrentUser actor) {
        Page<User> page = repository.findAll(pageable);
        boolean admin = actor != null && actor.isAdmin();
        return ApiPageResponse.of(
                page.getContent().stream().map(user -> toDto(user, admin)).toList(),
                pageable.getPageNumber(), pageable.getPageSize(), page.getTotalElements());
    }

    /** Email visible uniquement au propriétaire du compte ou à un ADMIN (SEC-002). */
    private static boolean emailVisibleTo(Long ownerUserId, CurrentUser actor) {
        return actor != null && actor.isAuthenticated()
                && (actor.isAdmin() || actor.id().equals(ownerUserId));
    }

    @Transactional
    public UserDto update(Long id, UserRequest request, CurrentUser actor) {
        CurrentUser.requireOwnerOrAdmin(actor, id);
        User user = findByIdOrThrow(id);
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        if (!user.getEmail().equals(email) && repository.existsByEmail(email)) {
            throw ApiException.conflict("Un compte existe déjà avec cet email : " + email);
        }
        user.updatePublicInfo(request.displayName().trim(), request.avatarUrl());
        return toDto(repository.save(user));
    }

    /**
     * Changement de rôle (ADMIN uniquement), ex. USER → CREATOR, CREATOR →
     * MODERATOR, MODERATOR → ADMIN.
     * Garde métier : il est INTERDIT à un admin de modifier son PROPRE rôle
     * (même {id} == {actor.id}) — impossible de s'octroyer une promotion par
     * soi-même, mais aussi impossible de se dégrader (on garde au moins un
     * administrateur). Un autre ADMIN peut en revanche dégrader/supromouvoir.
     */
    @Transactional
    public UserDto changeRole(Long id, User.Role role, CurrentUser actor) {
        CurrentUser.requireAuthenticated(actor);
        if (!actor.isAdmin()) {
            throw ApiException.forbidden("Seul un ADMIN peut changer les rôles");
        }
        if (actor.id().equals(id)) {
            throw ApiException.forbidden("Un admin ne peut pas modifier son propre rôle");
        }
        User user = findByIdOrThrow(id);
        user.changeRole(role);
        return toDto(repository.save(user));
    }

    @Transactional
    public void delete(Long id, CurrentUser actor) {
        CurrentUser.requireAuthenticated(actor);
        if (!actor.isAdmin()) {
            throw ApiException.forbidden("Seul un ADMIN peut supprimer un compte");
        }
        if (actor.id().equals(id)) {
            throw ApiException.forbidden("Un admin ne peut pas supprimer son propre compte");
        }
        repository.delete(findByIdOrThrow(id));
    }

    /** Charge le compte ou lève une 404 métier. */
    private User findByIdOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Compte introuvable (id=" + id + ")"));
    }

    /** Conversion interne entité → DTO (jamais d'entité exposée). */
    private UserDto toDto(User user) {
        return toDto(user, true);
    }

    /**
     * Conversion avec masquage de l'email (SEC-002) : les écritures (réponse
     * adressée au propriétaire ou à un ADMIN) exposent l'email ; les lectures
     * publiques le masquent ({@code null}) sauf propriétaire/ADMIN.
     */
    private UserDto toDto(User user, boolean emailVisible) {
        return new UserDto(user.getId(), emailVisible ? user.getEmail() : null, user.getDisplayName(),
                user.getAvatarUrl(), user.getRole(), user.getCreatedAt());
    }
}