package com.streamingafrique.backend.auth.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.streamingafrique.backend.auth.model.AuthSession;

/**
 * Accès aux sessions {auth_sessions}.
 *
 * Méthodes JUSTE nécessaires (résolution par empreinte au refresh/logout,
 * liste par compte, purge des expirées). AUCUNE logique métier ici.
 */
public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {

    /** Session par empreinte du refresh JWT (refresh/logout). */
    Optional<AuthSession> findByTokenHash(String tokenHash);

    /** Sessions non révoquées d'un compte (page /profile/settings/security). */
    List<AuthSession> findByUserIdAndRevokedAtIsNullOrderByLastUsedAtDesc(Long userId);

    /** Toutes les sessions d'un compte (révocation globale au changement de mot de passe). */
    List<AuthSession> findByUserId(Long userId);

    /** Purge des sessions expirées depuis plus longtemps que le seuil. */
    void deleteByExpiresAtBefore(Instant threshold);
}
