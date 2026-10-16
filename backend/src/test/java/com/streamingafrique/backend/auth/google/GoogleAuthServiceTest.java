package com.streamingafrique.backend.auth.google;

import java.util.Optional;

import com.streamingafrique.backend.auth.dto.LoginResponse;
import com.streamingafrique.backend.auth.dto.MeResponse;
import com.streamingafrique.backend.auth.service.AuthService;
import com.streamingafrique.backend.exception.ApiException;
import com.streamingafrique.backend.profile.model.UserProfile;
import com.streamingafrique.backend.profile.repository.UserProfileRepository;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Connexion Google — test UNITAIRE (validateur stubbé, sans réseau).
 *
 * Verrouille les 4 cas : création auto (CAS 1), reconnexion (CAS 2),
 * conflit email LOCAL sans doublon ni fusion (CAS 3), liaison explicite
 * et conflit de liaison. Le login classique reste couvert par
 * {@code AuthServiceTest} / {@code AuthFlowTest} (non-régression).
 */
@ExtendWith(MockitoExtension.class)
class GoogleAuthServiceTest {

    private static final GoogleProfile PROFILE = new GoogleProfile(
            "google-sub-123", "Marie.Dupont@Example.com", true,
            "Marie", "Dupont", "Marie Dupont", "https://photo.example/m.jpg");

    @Mock
    private AuthService authService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserProfileRepository profileRepository;

    private GoogleAuthService googleAuthService;

    private LoginResponse tokens() {
        return new LoginResponse("access", "Bearer", 900L, 7L,
                "marie.dupont@example.com", "Marie Dupont", User.Role.USER, "refresh", 604800L);
    }

    @BeforeEach
    void setUp() {
        googleAuthService = new GoogleAuthService(idToken -> PROFILE,
                authService, userRepository, profileRepository);
    }

    @Test
    @DisplayName("CAS 1 — premier passage : compte FLICK créé (USER, sans mot de passe, vérifié) + JWT")
    void firstLoginCreatesAccount() {
        when(userRepository.findByGoogleId("google-sub-123")).thenReturn(Optional.empty());
        when(userRepository.existsByEmail("marie.dupont@example.com")).thenReturn(false);
        when(userRepository.existsByUsername("marie.dupont")).thenReturn(false);
        when(authService.issueLogin(any(User.class), any(), any(), any())).thenReturn(tokens());

        LoginResponse response = googleAuthService.loginWithGoogle("raw-id-token", null, null);

        assertThat(response.accessToken()).isEqualTo("access");
        ArgumentCaptor<User> created = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(created.capture());
        assertThat(created.getValue().getEmail()).isEqualTo("marie.dupont@example.com");
        assertThat(created.getValue().getGoogleId()).isEqualTo("google-sub-123");
        assertThat(created.getValue().getAuthProvider()).isEqualTo(User.AuthProvider.GOOGLE);
        assertThat(created.getValue().getRole()).isEqualTo(User.Role.USER);
        assertThat(created.getValue().getPasswordHash()).isNull();
        assertThat(created.getValue().isEmailVerified()).isTrue();
        verify(profileRepository).save(any(UserProfile.class));
    }

    @Test
    @DisplayName("CAS 2 — google_id connu : reconnexion, profil rafraîchi, AUCUNE création")
    void knownGoogleIdReconnects() {
        User existing = User.create("marie.dupont@example.com", "marie.dupont",
                "Marie", "$2a$10$hash");
        existing.linkGoogle("google-sub-123");
        when(userRepository.findByGoogleId("google-sub-123")).thenReturn(Optional.of(existing));
        when(authService.issueLogin(any(User.class), any(), any(), any())).thenReturn(tokens());

        googleAuthService.loginWithGoogle("raw-id-token", null, null);

        verify(userRepository).save(existing);
        assertThat(existing.getDisplayName()).isEqualTo("Marie Dupont");
        assertThat(existing.getAvatarUrl()).isEqualTo("https://photo.example/m.jpg");
        verify(profileRepository, never()).save(any(UserProfile.class));
        verify(userRepository, never()).existsByEmail(any());
    }

    @Test
    @DisplayName("CAS 3 — email LOCAL existant : 409, AUCUN doublon, AUCUNE fusion")
    void existingLocalEmailConflicts() {
        when(userRepository.findByGoogleId("google-sub-123")).thenReturn(Optional.empty());
        when(userRepository.existsByEmail("marie.dupont@example.com")).thenReturn(true);

        assertThatThrownBy(() -> googleAuthService.loginWithGoogle("raw-id-token", null, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Connectez-vous avec votre mot de passe");

        verify(userRepository, never()).save(any(User.class));
        verify(profileRepository, never()).save(any(UserProfile.class));
        verify(authService, never()).issueLogin(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Liaison — compte connecté : google_id lié, 200")
    void linkGoogleBindsAuthenticatedAccount() {
        User account = User.create("marie.dupont@example.com", "marie.dupont",
                "Marie", "$2a$10$hash");
        when(userRepository.findById(7L)).thenReturn(Optional.of(account));
        when(userRepository.findByGoogleId("google-sub-123")).thenReturn(Optional.empty());

        MeResponse response = googleAuthService.linkGoogle(
                CurrentUser.of(7L, "marie.dupont@example.com", User.Role.USER), "raw-id-token");

        assertThat(response.email()).isEqualTo("marie.dupont@example.com");
        assertThat(account.getGoogleId()).isEqualTo("google-sub-123");
        assertThat(account.isEmailVerified()).isTrue();
        verify(userRepository).save(account);
    }

    @Test
    @DisplayName("Liaison — Google déjà lié à un AUTRE compte : 409")
    void linkGoogleConflictsWhenAlreadyBoundElsewhere() {
        User account = User.create("marie.dupont@example.com", "marie.dupont",
                "Marie", "$2a$10$hash");
        User other = User.createGoogle("autre@example.com", "autre", "Autre",
                "google-sub-123", null, true);
        ReflectionTestUtils.setField(account, "id", 7L);
        ReflectionTestUtils.setField(other, "id", 9L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(account));
        when(userRepository.findByGoogleId("google-sub-123")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> googleAuthService.linkGoogle(
                CurrentUser.of(7L, "marie.dupont@example.com", User.Role.USER), "raw-id-token"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("déjà lié");

        assertThat(account.getGoogleId()).isNull();
    }
}
