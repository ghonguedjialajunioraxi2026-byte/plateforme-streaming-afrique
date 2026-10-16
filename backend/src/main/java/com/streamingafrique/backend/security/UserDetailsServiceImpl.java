package com.streamingafrique.backend.security;

import java.util.List;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;

/**
 * Charge les comptes de la plateforme pour Spring Security.
 *
 * Identifiant de connexion = EMAIL (unique, colonne uk_users_email).
 * Le principal renvoyé est un {@link AuthenticatedUser} : il porte l'id du
 * compte et son rôle — exigés par l'autorisation FINE (étape 6.25).
 * Le rôle est traduit en authority "ROLE_<ROLE>" — c'est cette authority
 * que les règles d'autorisation comparent (ex. @PreAuthorize("hasRole('ADMIN')")
 * se traduit en hasAuthority("ROLE_ADMIN"), avec la hiérarchie USER < CREATOR
 * < MODERATOR < ADMIN).
 *
 * Un compte SANS mot de passe (passwordHash null, ex. comptes créés avant
 * le socle sécurité) ne peut s'authentifier : on renvoie un mot de passe vide,
 * la comparaison BCrypt échoue toujours → accès refusé proprement.
 */
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Aucun compte : " + email));

        return AuthenticatedUser.of(user);
    }
}