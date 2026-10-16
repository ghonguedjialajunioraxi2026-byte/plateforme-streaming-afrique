package com.streamingafrique.backend.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.streamingafrique.backend.user.model.User;

/**
 * Principal Spring Security (étape 6.25).
 *
 * Remplace le simple {@code org.springframework.security.core.userdetails.User} :
 * en plus de l'email (nom d'utilisateur) et du mot de passe haché, il porte
 * l'IDENTIFIANT du compte ({@code id}) et son RÔLE ({@code role}).
 *
 * Pourquoi ? L'autorisation fine (étape 6.25) a besoin du QUI : « cet utilisateur
 * est-il le PROPRIÉTAIRE de la ressource ? ». Le contrôleur récupère alors
 * {@link CurrentUser#from} depuis l'{@code Authentication} et vérifie la
 * propriété dans le service — plus jamais d'identité « déclarative » dans le
 * payload ou les query params.
 *
 * La source de vérité reste PostgreSQL : ce principal est construit par
 * {@link UserDetailsServiceImpl} après chargement du compte (Basic) ou par le
 * {@code JwtAuthenticationFilter} (JWT → email → reload compte).
 */
public record AuthenticatedUser(Long id, String email, String password, User.Role role, boolean enabled)
        implements UserDetails {

    public static AuthenticatedUser of(User user) {
        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getPasswordHash() != null
                ? user.getPasswordHash() : "", user.getRole(),
                user.getStatus() == User.Status.ACTIVE);
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}