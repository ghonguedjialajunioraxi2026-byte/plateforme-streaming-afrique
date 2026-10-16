package com.streamingafrique.backend.security;

import com.streamingafrique.backend.exception.ApiException;
import com.streamingafrique.backend.user.model.User;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Stratégie de tests (étape 6.29) — test UNITAIRE pur du cœur de
 * l'autorisation fine (étape 6.25).
 *
 * Les gardes {@code CurrentUser} sont la dernière ligne de défense des
 * écritures sensibles (suivis, notifications, profil, rôles) : un garde qui
 * laisse passer une usurpation d'identité = faille critique. On les teste
 * donc EXHAUSTIVEMENT ici en unitaire (rapide, déterministe), en complément
 * des tests d'intégration HTTP qui vérifient le câblage contrôleur → service.
 *
 * Convention : chaque garde refusée lève une {@link ApiException} 403
 * {@code FORBIDDEN} (jamais de 500, jamais de fuite d'information).
 */
class CurrentUserTest {

    private static final CurrentUser ADMIN = CurrentUser.of(1L, "admin@test.fr", User.Role.ADMIN);
    private static final CurrentUser MODERATOR = CurrentUser.of(2L, "modo@test.fr", User.Role.MODERATOR);
    private static final CurrentUser CREATOR = CurrentUser.of(3L, "crea@test.fr", User.Role.CREATOR);
    private static final CurrentUser VIEWER = CurrentUser.of(4L, "viewer@test.fr", User.Role.USER);

    // -------------------------------------------------- construction

    @Test
    @DisplayName("from() : principal AuthenticatedUser → acteur complet")
    void from_principal_valide() {
        var authentication = new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(7L, "u@test.fr", "hash", User.Role.CREATOR, true), null);

        CurrentUser actor = CurrentUser.from(authentication);

        assertThat(actor).isEqualTo(CurrentUser.of(7L, "u@test.fr", User.Role.CREATOR));
    }

    @Test
    @DisplayName("from() : authentification nulle ou principal inattendu → null (anonyme)")
    void from_anonyme() {
        assertThat(CurrentUser.from(null)).isNull();
        assertThat(CurrentUser.from(
                new UsernamePasswordAuthenticationToken("simple-string", null))).isNull();
    }

    // -------------------------------------------------- hiérarchie

    @Test
    @DisplayName("Hiérarchie USER < CREATOR < MODERATOR < ADMIN")
    void hierarchie() {
        assertThat(ADMIN.hasRank(User.Role.USER)).isTrue();
        assertThat(MODERATOR.hasRank(User.Role.CREATOR)).isTrue();
        assertThat(CREATOR.hasRank(User.Role.MODERATOR)).isFalse();
        assertThat(VIEWER.hasRank(User.Role.USER)).isTrue();
        assertThat(VIEWER.isAdmin()).isFalse();
        assertThat(ADMIN.isAdmin()).isTrue();
        assertThat(MODERATOR.isModeratorOrAbove()).isTrue();
        assertThat(CREATOR.isModeratorOrAbove()).isFalse();
    }

    // -------------------------------------------------- gardes

    @Test
    @DisplayName("requireAuthenticated : anonyme (null) et id null → 403")
    void requireAuthenticated() {
        CurrentUser.requireAuthenticated(VIEWER);
        assertThatThrownBy(() -> CurrentUser.requireAuthenticated(null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(((ApiException) ex).getErrorCode()).isEqualTo("FORBIDDEN");
                });
    }

    @Test
    @DisplayName("requireSelf : soi-même OK (même ADMIN), autrui refusé — y compris pour un ADMIN")
    void requireSelf() {
        CurrentUser.requireSelf(VIEWER, 4L);
        CurrentUser.requireSelf(ADMIN, 1L);

        // Point critique : même un ADMIN ne peut pas agir AU NOM d'un autre
        // (les abonnements et lectures sont personnels).
        assertThatThrownBy(() -> CurrentUser.requireSelf(ADMIN, 4L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus())
                        .isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> CurrentUser.requireSelf(VIEWER, 3L))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> CurrentUser.requireSelf(null, 4L))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("requireOwnerOrAdmin : propriétaire ou ADMIN, tiers refusé")
    void requireOwnerOrAdmin() {
        CurrentUser.requireOwnerOrAdmin(VIEWER, 4L);
        CurrentUser.requireOwnerOrAdmin(ADMIN, 4L);

        assertThatThrownBy(() -> CurrentUser.requireOwnerOrAdmin(VIEWER, 3L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("requireOwnerOrModerator : propriétaire ou MODERATOR+, simple USER tiers refusé")
    void requireOwnerOrModerator() {
        CurrentUser.requireOwnerOrModerator(CREATOR, 3L);
        CurrentUser.requireOwnerOrModerator(MODERATOR, 3L);
        CurrentUser.requireOwnerOrModerator(ADMIN, 3L);

        assertThatThrownBy(() -> CurrentUser.requireOwnerOrModerator(VIEWER, 3L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }
}
