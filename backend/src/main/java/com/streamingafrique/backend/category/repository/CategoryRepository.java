package com.streamingafrique.backend.category.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.streamingafrique.backend.category.model.Category;

/**
 * Couche Repository : accès à la base pour la ressource Category.
 * Spring Data JPA fournit findAll, findById, save, delete... gratuitement.
 * On ajoute ici uniquement les requêtes métier spécifiques.
 *
 * {@link JpaSpecificationExecutor} : recherche dynamique (nom/slug/description).
 */
public interface CategoryRepository extends JpaRepository<Category, Long>, JpaSpecificationExecutor<Category> {

    /** La catégorie existe-t-elle déjà ? (vérification insensible à la casse) */
    boolean existsByNameIgnoreCase(String name);

    /** Un slug est-il déjà pris ? (les slugs sont uniques) */
    boolean existsBySlug(String slug);

    /** Catégorie par slug (URL publique, seed idempotent futur). */
    Optional<Category> findBySlug(String slug);
}