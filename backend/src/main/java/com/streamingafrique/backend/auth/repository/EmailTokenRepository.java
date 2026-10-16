package com.streamingafrique.backend.auth.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.streamingafrique.backend.auth.model.EmailToken;

/**
 * Accès aux jetons email {email_tokens}.
 *
 * Méthodes JUSTE nécessaires (consommation par empreinte, invalidation des
 * jetons précédents à la réémission). AUCUNE logique métier ici.
 */
public interface EmailTokenRepository extends JpaRepository<EmailToken, Long> {

    /** Jeton par empreinte (vérification / reset). */
    Optional<EmailToken> findByTokenHash(String tokenHash);

    /** Jetons d'un compte pour un usage (invalidation à la réémission). */
    List<EmailToken> findByUserIdAndType(Long userId, EmailToken.Type type);
}
