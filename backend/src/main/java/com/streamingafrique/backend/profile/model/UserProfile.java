package com.streamingafrique.backend.profile.model;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import com.streamingafrique.backend.common.model.BaseEntity;
import com.streamingafrique.backend.user.model.User;

/**
 * Entité "UserProfile" → table {@code profiles}.
 *
 * RÔLE : l'extension du compte spectateur (sous-étape 7.10). Complète
 * l'entité {@link User} sans la modifier : un User a 0 ou 1 UserProfile.
 *
 * Séparation users/profiles (décision 7.10) : l'identité d'authentification
 * (email, username, passwordHash, rôle, statut) et l'identité affichée de
 * base (displayName, avatarUrl) restent dans {@code users} — lus à chaque
 * requête d'authentification et dans tous les DTO. Tout le reste
 * (présentation étendue, localisation, réseaux, naissance, préférences)
 * vit ici : la table chaude {@code users} reste étroite, le profil
 * s'allonge sans migration de la table critique.
 *
 * Relation 1-1 UNIDIRECTIONNELLE (même pattern que CreatorProfile) : la
 * colonne {@code user_id} vit ici, unique et NOT NULL. {@link User} n'a
 * AUCUN champ en retour → pas de référence circulaire.
 */
@Entity
@Table(name = "profiles", indexes = {
        @Index(name = "uk_profiles_user", columnList = "user_id", unique = true)
})
public class UserProfile extends BaseEntity {

    /** Le compte propriétaire de ce profil. Jamais null (index unique). */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /** Présentation libre. */
    @Column(length = 500)
    private String bio;

    /** Pays ISO alpha-2 (ex. "SN", "CI") — NULL = non renseigné. */
    @Column(name = "country_code", length = 2)
    private String countryCode;

    /** Langue préférée (ex. "fr", "en", "wo") — NULL = défaut plateforme. */
    @Column(length = 10)
    private String language;

    /**
     * Fuseau IANA (ex. "Africa/Douala") — NULL = défaut plateforme.
     * Sert à afficher les lives programmés à l'heure locale.
     */
    @Column(length = 60)
    private String timezone;

    /** Devise préférée ISO-3 (ex. "XAF") — NULL = devise du pays/défaut. */
    @Column(name = "preferred_currency", length = 3)
    private String preferredCurrency;

    /** Site personnel. */
    @Column(length = 500)
    private String websiteUrl;

    /** Pseudonymes/URL réseaux — NULL = non renseigné. */
    @Column(length = 100)
    private String xHandle;

    @Column(length = 100)
    private String instagramHandle;

    @Column(length = 500)
    private String youtubeUrl;

    /**
     * Date de naissance (optionnelle, ex. contrôle d'âge futur).
     * Donnée sensible : jamais exposée par défaut, jamais obligatoire.
     */
    @Column(name = "birth_date")
    private LocalDate birthDate;

    /** M'alerter quand une chaîne suivie passe en direct. */
    @Column(nullable = false)
    private boolean notifyLive = true;

    /** M'alerter des nouveaux contenus (vidéos, clips). */
    @Column(nullable = false)
    private boolean notifyContent = true;

    /** Profil visible publiquement. */
    @Column(name = "is_public", nullable = false)
    private boolean isPublic = true;

    /** Constructeur vide requis par JPA (ne pas utiliser autrement). */
    protected UserProfile() {
    }

    /** Fabrique : un profil naît TOUJOURS rattaché à un compte existant. */
    public static UserProfile create(User user) {
        UserProfile profile = new UserProfile();
        profile.user = user;
        return profile;
    }

    /** Mise à jour de la présentation et de la localisation. */
    public void updatePresentation(String bio, String countryCode, String language) {
        this.bio = bio;
        this.countryCode = countryCode;
        this.language = language;
    }

    /** Mise à jour des liens externes. */
    public void updateLinks(String websiteUrl, String xHandle, String instagramHandle, String youtubeUrl) {
        this.websiteUrl = websiteUrl;
        this.xHandle = xHandle;
        this.instagramHandle = instagramHandle;
        this.youtubeUrl = youtubeUrl;
    }

    /** Mise à jour de la date de naissance (NULL = retrait). */
    public void updateBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    /** Mise à jour des préférences non sensibles. */
    public void updatePreferences(boolean notifyLive, boolean notifyContent, boolean isPublic) {
        this.notifyLive = notifyLive;
        this.notifyContent = notifyContent;
        this.isPublic = isPublic;
    }

    /** Préférences régionales (étape 19) — valeurs déjà validées par le service. */
    public void updateRegionPrefs(String timezone, String preferredCurrency) {
        this.timezone = timezone;
        this.preferredCurrency = preferredCurrency;
    }

    public User getUser() {
        return user;
    }

    public String getBio() {
        return bio;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getLanguage() {
        return language;
    }

    public String getTimezone() {
        return timezone;
    }

    public String getPreferredCurrency() {
        return preferredCurrency;
    }

    public String getWebsiteUrl() {
        return websiteUrl;
    }

    public String getXHandle() {
        return xHandle;
    }

    public String getInstagramHandle() {
        return instagramHandle;
    }

    public String getYoutubeUrl() {
        return youtubeUrl;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public boolean isNotifyLive() {
        return notifyLive;
    }

    public boolean isNotifyContent() {
        return notifyContent;
    }

    public boolean isPublic() {
        return isPublic;
    }
}
