package com.streamingafrique.backend.category.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.streamingafrique.backend.common.model.BaseEntity;

/**
 * Entité "Category" → table {@code categories}.
 *
 * ⚠️ Classe INTERNE au backend : une entité JPA n'est JAMAIS renvoyée
 * telle quelle par l'API. Le controller passe par le DTO CategoryDto.
 *
 * L'identifiant et les dates d'audit (createdAt/updatedAt) sont hérités
 * de {@link BaseEntity} — ici on ne décrit QUE les colonnes propres
 * à une catégorie.
 *
 * Extensible (sous-étape 7.12) : image, statut ACTIVE/ARCHIVED (jamais de
 * suppression physique si utilisée), ordre d'affichage pour le catalogue,
 * parent optionnel pour de futures sous-catégories (un seul niveau tant
 * que le besoin n'est pas tranché — pas de hiérarchie récursive profonde).
 */
@Entity
@Table(name = "categories")
public class Category extends BaseEntity {

    /**
     * Statut du catalogue (sous-étape 7.8) : ACTIVE ↔ ARCHIVED.
     * Une catégorie utilisée par un stream/vidéo ne se supprime jamais :
     * on l'archive (elle disparaît du catalogue mais les contenus restent liés).
     */
    public enum Status {
        ACTIVE,
        ARCHIVED
    }

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(nullable = false, unique = true, length = 60)
    private String slug;

    @Column(length = 500)
    private String description;

    /** Visuel du catalogue (bandeau/icône) — NULL tant que non fourni. */
    @Column(length = 500)
    private String imageUrl;

    /** Statut par défaut : active. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    /** Ordre d'affichage dans le catalogue (croissant, 0 = tête). */
    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    /**
     * Catégorie parente (sous-catégories futures) — NULL = racine.
     * LAZY + unidirectionnel : pas de collection enfants (pas de boucle).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    /** Constructeur vide requis par JPA (ne pas utiliser autrement). */
    protected Category() {
    }

    /** Fabrique : la création passe TOUJOURS par ici pour garantir la cohérence. */
    public static Category create(String name, String slug, String description) {
        Category category = new Category();
        category.name = name;
        category.slug = slug;
        category.description = description;
        category.status = Status.ACTIVE;
        category.displayOrder = 0;
        return category;
    }

    /** Mise à jour des champs modifiables par l'API (signature conservée). */
    public void update(String name, String slug, String description) {
        this.name = name;
        this.slug = slug;
        this.description = description;
    }

    /** Mise à jour du visuel et de l'ordre d'affichage. */
    public void updateDisplay(String imageUrl, int displayOrder) {
        this.imageUrl = imageUrl;
        this.displayOrder = displayOrder;
    }

    /** Archivage / réactivation (jamais de suppression si utilisée). */
    public void changeStatus(Status status) {
        this.status = status;
    }

    /** Rattachement à une catégorie parente (NULL = racine). */
    public void updateParent(Category parent) {
        this.parent = parent;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public String getDescription() {
        return description;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public Status getStatus() {
        return status;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public Category getParent() {
        return parent;
    }
}