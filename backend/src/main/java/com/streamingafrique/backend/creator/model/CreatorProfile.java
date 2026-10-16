package com.streamingafrique.backend.creator.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import com.streamingafrique.backend.category.model.Category;
import com.streamingafrique.backend.common.model.BaseEntity;
import com.streamingafrique.backend.user.model.User;

/**
 * Entité "CreatorProfile" → table {@code creator_profiles}.
 *
 * RÔLE : la fiche PUBLIQUE du créateur (sa chaîne). Complète l'entité
 * {@link User} sans la modifier : un User a 0 ou 1 CreatorProfile.
 *
 * Relation 1-1 UNIDIRECTIONNELLE : la colonne {@code user_id} vit ici,
 * créateur et unique. {@link User} n'a AUCUN champ en retour → pas de
 * référence circulaire, pas de souci de sérialisation (nos DTOs restent
 * plats de toute façon).
 *
 * Création : la fabrique tient compte du fait qu'aucun créateur ne peut
 * pointer vers un compte inexistant (nullable = false).
 *
 * Statistiques (followers, vues, nombre de streams — sous-étape 7.11) :
 * AUCUN compteur stocké. Ils se calculent via les tables sources
 * (follows, streams, videos, stat_snapshots) : un compteur dénormalisé
 * dériverait à la première écriture concurrente et coûterait une
 * synchronisation permanente pour un gain de lecture nul (comptages
 * indexés + cache Redis TTL court).
 */
@Entity
@Table(name = "creator_profiles", indexes = {
        @Index(name = "uk_creator_profiles_user", columnList = "user_id", unique = true),
        @Index(name = "uk_creator_profiles_slug", columnList = "slug", unique = true)
})
public class CreatorProfile extends BaseEntity {

    /** Nom de la chaîne affiché publiquement. */
    @Column(nullable = false, length = 100)
    private String displayName;

    /** URL conviviale de la chaîne ("/@ada-lovelace") — unique. */
    @Column(nullable = false, length = 60)
    private String slug;

    @Column(length = 500)
    private String bio;

    @Column(length = 500)
    private String avatarUrl;

    @Column(length = 500)
    private String bannerUrl;

    /**
     * Statut de la chaîne (sous-étape 7.8) : ACTIVE → SUSPENDED → ACTIVE / CLOSED.
     * Suspension = modération ; fermeture = fin de chaîne (slug gelé, contenus
     * masqués). Jamais de suppression physique avec activité.
     */
    public enum Status {
        ACTIVE,
        SUSPENDED,
        CLOSED
    }

    /** Le compte propriétaire de cette fiche. Jamais null (index unique uk_creator_profiles_user). */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /**
     * Catégorie principale de la chaîne (mise en avant, découverte).
     * NULL = non renseignée. Les streams/vidéos gardent leur propre catégorie.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_category_id")
    private Category primaryCategory;

    /** Statut par défaut : active. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    /** Constructeur vide requis par JPA (ne pas utiliser autrement). */
    protected CreatorProfile() {
    }

    /** Fabrique : la création passe TOUJOURS par ici pour garantir la cohérence. */
    public static CreatorProfile create(User user, String displayName, String slug) {
        CreatorProfile profile = new CreatorProfile();
        profile.user = user;
        profile.displayName = displayName;
        profile.slug = slug;
        profile.status = Status.ACTIVE;
        return profile;
    }

    /** Mise à jour des informations de la chaîne. */
    public void updateChannelInfo(String displayName, String bio, String avatarUrl, String bannerUrl) {
        this.displayName = displayName;
        this.bio = bio;
        this.avatarUrl = avatarUrl;
        this.bannerUrl = bannerUrl;
    }

    /** Mise à jour du slug (renommée de chaîne) — unicité vérifiée par le service. */
    public void updateSlug(String slug) {
        this.slug = slug;
    }

    /** Changement de statut (suspension / réactivation / fermeture). */
    public void changeStatus(Status status) {
        this.status = status;
    }

    /** Catégorie principale (NULL = retrait de la mise en avant). */
    public void updatePrimaryCategory(Category primaryCategory) {
        this.primaryCategory = primaryCategory;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getSlug() {
        return slug;
    }

    public String getBio() {
        return bio;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public String getBannerUrl() {
        return bannerUrl;
    }

    public User getUser() {
        return user;
    }

    public Category getPrimaryCategory() {
        return primaryCategory;
    }

    public Status getStatus() {
        return status;
    }
}