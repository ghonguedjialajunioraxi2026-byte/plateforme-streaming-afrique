package com.streamingafrique.backend.user.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.streamingafrique.backend.user.model.User;

/**
 * Accès aux comptes {users}.
 *
 * Méthodes JUSTE nécessaires (connexion + brouillon de doublon à
 * l'inscription). Pagination/tri viennent de JpaRepository (findAll(Pageable)).
 * AUCUNE logique métier ici — juste de l'accès aux données.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /** Récupère un compte par son identifiant de connexion (colonne unique). */
    Optional<User> findByEmail(String email);

    /** Vrai si l'email est déjà utilisé (contrôle d'unicité à l'inscription). */
    boolean existsByEmail(String email);

    /** Récupère un compte par son pseudonyme unique (mentions, URL publique). */
    Optional<User> findByUsername(String username);

    /** Vrai si le pseudonyme est déjà utilisé (contrôle d'unicité à l'inscription). */
    boolean existsByUsername(String username);

    /**
     * Récupère un compte par son identifiant Google (claim "sub", V24).
     * Un compte Google = au plus UN compte FLICK (uk_users_google_id).
     */
    Optional<User> findByGoogleId(String googleId);

    /**
     * Recherche admin paginée (étape 17) : texte (email, pseudo, nom affiché,
     * insensible à la casse) + filtres rôle/statut optionnels.
     */
    @Query("""
            select u from User u
             where (:search is null
                    or lower(u.email) like lower(concat('%', :search, '%'))
                    or lower(u.username) like lower(concat('%', :search, '%'))
                    or lower(u.displayName) like lower(concat('%', :search, '%')))
               and (:role is null or u.role = :role)
               and (:status is null or u.status = :status)
            """)
    Page<User> search(@Param("search") String search,
                      @Param("role") User.Role role,
                      @Param("status") User.Status status,
                      Pageable pageable);

    /** Comptes par statut (pilotage admin). */
    long countByStatus(User.Status status);

    /** Comptes par rôle (pilotage admin). */
    long countByRole(User.Role role);
}