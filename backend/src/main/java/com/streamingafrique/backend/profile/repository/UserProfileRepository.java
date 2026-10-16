package com.streamingafrique.backend.profile.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.streamingafrique.backend.profile.model.UserProfile;

/**
 * Accès aux profils utilisateurs {profiles}.
 *
 * Méthodes JUSTE nécessaires (profil par compte, contrôle d'existence
 * 1-1). AUCUNE logique métier ici.
 */
public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    /** Profil d'un compte donné (relation 1-1 côté profil). */
    Optional<UserProfile> findByUserId(Long userId);

    /** Vrai si ce compte possède déjà un profil. */
    boolean existsByUserId(Long userId);
}
