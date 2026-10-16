package com.streamingafrique.backend.auth.service;

import java.time.Duration;
import java.util.Optional;

import com.streamingafrique.backend.auth.dto.LoginRequest;
import com.streamingafrique.backend.auth.dto.LoginResponse;
import com.streamingafrique.backend.auth.mail.MailService;
import com.streamingafrique.backend.auth.repository.AuthSessionRepository;
import com.streamingafrique.backend.auth.repository.EmailTokenRepository;
import com.streamingafrique.backend.exception.ApiException;
import com.streamingafrique.backend.profile.repository.UserProfileRepository;
import com.streamingafrique.backend.security.jwt.JwtProperties;
import com.streamingafrique.backend.security.jwt.JwtService;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Stratégie de tests (étape 6.29) — test UNITAIRE du service de connexion,
 * dépendances mockées (Mockito), jetons RÉELS (vrai {@link JwtService}).
 *
 * Le login est le chemin le plus critique de la plateforme : on verrouille
 * ici la normalisation de l'email, la propagation des échecs d'authentification
 * et le contenu de la réponse, en millisecondes et sans base — le parcours
 * HTTP complet reste couvert par {@code JwtAuthenticationTest}.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserProfileRepository profileRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthSessionRepository sessionRepository;
    @Mock
    private EmailTokenRepository emailTokenRepository;
    @Mock
    private MailService mailService;
    @Mock
    private User user;
    @Mock
    private com.streamingafrique.backend.beta.service.BetaService betaService;

    private JwtService jwtService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("cle-secrete-de-tests-64-octets-0123456789abcdef0123456789");
        properties.setAccessTokenTtl(Duration.ofMinutes(15));
        properties.setRefreshTokenTtl(Duration.ofDays(7));
        jwtService = new JwtService(properties);
        authService = new AuthService(authenticationManager, jwtService, userRepository,
                profileRepository, passwordEncoder, sessionRepository, emailTokenRepository,
                mailService, betaService, "http://localhost:5173");
    }

    @Test
    @DisplayName("L'email est normalisé (casse + espaces) avant authentification et recherche")
    void email_normalise() {
        User account = account();
        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenReturn(new UsernamePasswordAuthenticationToken("x", "y"));
        when(userRepository.findByEmail("viewer@test.fr")).thenReturn(Optional.of(account));

        authService.login(new LoginRequest("  VIEWER@Test.FR  ", "motdepasse-xyz"));

        ArgumentCaptor<UsernamePasswordAuthenticationToken> credentials =
                ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
        verify(authenticationManager).authenticate(credentials.capture());
        assertThat(credentials.getValue().getPrincipal()).isEqualTo("viewer@test.fr");
        assertThat(credentials.getValue().getCredentials()).isEqualTo("motdepasse-xyz");
        verify(userRepository).findByEmail("viewer@test.fr");
    }

    @Test
    @DisplayName("Succès : réponse Bearer complète + jeton d'accès réellement valide")
    void login_succes() {
        User account = account();
        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenReturn(new UsernamePasswordAuthenticationToken("x", "y"));
        when(userRepository.findByEmail("viewer@test.fr")).thenReturn(Optional.of(account));

        LoginResponse response = authService.login(new LoginRequest("viewer@test.fr", "motdepasse-xyz"));

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isGreaterThan(0);
        assertThat(response.email()).isEqualTo("viewer@test.fr");
        assertThat(response.displayName()).isEqualTo("Viewer");
        assertThat(response.role()).isEqualTo(User.Role.USER);
        assertThat(response.userId()).isEqualTo(7L);
        assertThat(jwtService.isValidAccessToken(response.accessToken())).isTrue();
        assertThat(jwtService.extractUsername(response.accessToken())).isEqualTo("viewer@test.fr");
    }

    @Test
    @DisplayName("Mauvais mot de passe : l'exception remonte, aucun jeton n'est cherché en base")
    void mauvais_mot_de_passe() {
        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("viewer@test.fr", "faux")))
                .isInstanceOf(BadCredentialsException.class);

        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    @DisplayName("Compte supprimé entre authentification et relecture → 404 métier (jamais de 500)")
    void compte_supprime_apres_auth() {
        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenReturn(new UsernamePasswordAuthenticationToken("x", "y"));
        when(userRepository.findByEmail("viewer@test.fr")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("viewer@test.fr", "motdepasse-xyz")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    private User account() {
        when(user.getId()).thenReturn(7L);
        // Note : AuthService réutilise l'email NORMALISÉ local, il n'appelle
        // jamais user.getEmail() — aucun stub ici (strict stubs Mockito).
        when(user.getDisplayName()).thenReturn("Viewer");
        when(user.getRole()).thenReturn(User.Role.USER);
        return user;
    }
}
