package com.streamingafrique.backend.user.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import com.streamingafrique.backend.common.model.BaseEntity;

/**
 * Entité "User" → table {@code users}.
 *
 * RÔLE : le compte de la plateforme. Un visiteur crée un compte, puis un
 * compte peut devenir CRÉATEUR de contenu (sa fiche publique est alors
 * un {@code CreatorProfile}).
 *
 * Architecture choisie :
 *   - identifiant + dates d'audit hérités de {@link BaseEntity} ;
 *   - relation 1-1 vers CreatorProfile = UNIDIRECTIONNELLE (côté
 *     CreatorProfile uniquement) : aucun retour dans "users", aucune
 *     référence circulaire, plus rien à craindre en sérialisation.
 */
@Entity
@Table(name = "users", indexes = {
        @Index(name = "uk_users_email", columnList = "email", unique = true),
        @Index(name = "uk_users_username", columnList = "username", unique = true)
})
public class User extends BaseEntity {

    /**
     * Rôle dans la plateforme — la base de l'AUTORISATION (Spring Security).
     * Hiérarchie : USER < CREATOR < MODERATOR < ADMIN.
     * Chaque rôle est mappé en authority "ROLE_<nom>" par UserDetailsServiceImpl,
     * et la hiérarchie est portée par un bean RoleHierarchy (USER ⊂ CREATOR ⊂
     * MODERATOR ⊂ ADMIN) : un MODERATOR « est » aussi un CREATOR et un USER.
     *
     * {@code rank} permet une comparaison ordinale déterministe côté service
     * (ex. « a-t-il au moins le rang MODERATOR ? »).
     */
    public enum Role {
        /** Utilisateur classique : consulte, suit et interagit avec le contenu. */
        USER(1),
        /** Créateur : publie des streams/vidéos, gère sa chaîne et son profil. */
        CREATOR(2),
        /** Modérateur : modère le contenu et la communauté. */
        MODERATOR(3),
        /** Administrateur : administration globale (comptes, rôles, configuration). */
        ADMIN(4);

        private final int rank;

        Role(int rank) {
            this.rank = rank;
        }

        /** Rang ordinal de la hiérarchie (USER < CREATOR < MODERATOR < ADMIN). */
        public int rank() {
            return rank;
        }

        /** Ce rôle a-t-il au moins le rang {@code required} dans la hiérarchie ? */
        public boolean atLeast(Role required) {
            return this.rank >= required.rank();
        }
    }

    /**
     * Méthode d'authentification d'origine du compte (connexion Google, V24).
     * LOCAL = créé par email/mot de passe ; GOOGLE = créé via "Continuer
     * avec Google". Un compte LOCAL peut ensuite LIER un google_id (liaison
     * explicite après login mot de passe) sans changer ce provider.
     */
    public enum AuthProvider {
        LOCAL,
        GOOGLE
    }

    /**
     * Statut du compte (sous-étape 7.8) : ACTIVE → SUSPENDED → ACTIVE / CLOSED.
     * Bannissement = sanction en modération + SUSPENDED ; suppression = CLOSED
     * + anonymisation. Jamais de suppression physique avec activité.
     */
    public enum Status {
        ACTIVE,
        SUSPENDED,
        CLOSED
    }

    /** Identifiant de connexion. Unique (index uk_users_email). */
    @Column(nullable = false, length = 150)
    private String email;

    /**
     * Pseudonyme unique (connexion alternative, mentions @username, URL
     * publique future). Dérivé de l'email à la création si non fourni ;
     * modifiable ensuite via {@link #rename(String)}.
     */
    @Column(nullable = false, length = 50)
    private String username;

    /** Nom affiché publiquement (non unique, modifiable librement). */
    @Column(nullable = false, length = 100)
    private String displayName;

    /**
     * Hachage du mot de passe (BCrypt) — JAMAIS en clair, jamais exposé.
     * Le hachage est produit par le service (PasswordEncoder), jamais ici.
     */
    @Column(nullable = true, length = 100)
    private String passwordHash;

    @Column(length = 500)
    private String avatarUrl;

    /** Rôle par défaut : spectateur. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.USER;

    /** Statut par défaut : actif. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    /**
     * Dernière connexion réussie (JWT/refresh).
     * NULL = jamais connecté. Mise à jour via {@link #recordLogin()},
     * jamais à l'écriture du profil.
     */
    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /**
     * Acceptation des CGU (étape 25, V26) : horodatage du consentement
     * recueilli à l'inscription (case obligatoire). NULL = compte créé
     * avant la bêta publique (consentement implicite historique).
     */
    @Column(name = "terms_accepted_at")
    private Instant termsAcceptedAt;

    /**
     * Adresse email vérifiée (étape 08 : POST /api/auth/verify-email).
     * FALSE par défaut à l'inscription ; TRUE après validation du token
     * à usage unique. Ni le login ni les lectures n'en dépendent (pas de
     * blocage), mais le frontend l'affiche et la réclamera pour les
     * actions sensibles futures (paiements, retraits).
     */
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    /**
     * Identifiant stable du compte Google (claim "sub" de l'id_token OIDC,
     * V24). NULL = aucun compte Google lié. UNIQUE : un compte Google ne se
     * lie qu'à UN seul compte FLICK (contrainte uk_users_google_id).
     */
    @Column(name = "google_id", length = 255, unique = true)
    private String googleId;

    /**
     * Méthode de création du compte. LOCAL par défaut (email/mot de passe) ;
     * GOOGLE pour les comptes créés via "Continuer avec Google" (sans mot
     * de passe initial — voir "mot de passe oublié" pour en définir un).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 20)
    private AuthProvider authProvider = AuthProvider.LOCAL;

    /** Constructeur vide requis par JPA (ne pas utiliser autrement). */
    protected User() {
    }

    /**
     * Fabrique : la création passe TOUJOURS par ici pour garantir la cohérence.
     * Le username est dérivé de la partie locale de l'email (avant @).
     */
    public static User create(String email, String displayName, String passwordHash) {
        return create(email, deriveUsername(email), displayName, passwordHash);
    }

    /** Fabrique avec username explicite (inscription future). */
    public static User create(String email, String username, String displayName, String passwordHash) {
        User user = new User();
        user.email = email;
        user.username = username;
        user.displayName = displayName;
        user.passwordHash = passwordHash;
        user.role = Role.USER;
        user.status = Status.ACTIVE;
        user.authProvider = AuthProvider.LOCAL;
        return user;
    }

    /**
     * Fabrique Google (V24) : compte créé automatiquement à la première
     * connexion via "Continuer avec Google". Sans mot de passe (NULL) —
     * l'utilisateur pourra en définir un via "mot de passe oublié".
     * L'email n'est marqué vérifié que si Google le certifie
     * (claim email_verified de l'id_token).
     */
    public static User createGoogle(String email, String username, String displayName,
                                    String googleId, String avatarUrl, boolean emailVerified) {
        User user = new User();
        user.email = email;
        user.username = username;
        user.displayName = displayName;
        user.passwordHash = null;
        user.avatarUrl = avatarUrl;
        user.role = Role.USER;
        user.status = Status.ACTIVE;
        user.googleId = googleId;
        user.authProvider = AuthProvider.GOOGLE;
        user.emailVerified = emailVerified;
        return user;
    }

    /** Dérive un username de l'email (partie avant @, minuscules, max 50). */
    public static String deriveUsername(String email) {
        if (email == null) {
            return "user";
        }
        String local = email.trim().toLowerCase();
        int at = local.indexOf('@');
        if (at > 0) {
            local = local.substring(0, at);
        }
        local = local.replaceAll("[^a-z0-9._-]", "");
        if (local.isBlank()) {
            local = "user";
        }
        return local.length() > 50 ? local.substring(0, 50) : local;
    }

    /** Mise à jour des informations publiques de profil. */
    public void updatePublicInfo(String displayName, String avatarUrl) {
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
    }

    /** Changement de rôle (ex. USER → CREATOR quand la fiche créateur est créée). */
    public void changeRole(Role role) {
        this.role = role;
    }

    /** Changement de statut (suspension / réactivation / fermeture). */
    public void changeStatus(Status status) {
        this.status = status;
    }

    /**
     * Remplace le hachage du mot de passe (étape 08 : reset, changement).
     * Reçoit un HASH (BCrypt, produit par le service) — jamais de clair ici.
     */
    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /** Renommage du pseudonyme unique (unicité vérifiée par le service). */
    public void rename(String username) {
        this.username = username;
    }

    /** Marque une connexion réussie. */
    public void recordLogin() {
        this.lastLoginAt = Instant.now();
    }

    /** Enregistre le consentement aux CGU (inscription, case obligatoire). */
    public void acceptTerms() {
        this.termsAcceptedAt = Instant.now();
    }

    /** Marque l'adresse email comme vérifiée (token à usage unique consommé). */
    public void markEmailVerified() {
        this.emailVerified = true;
    }

    /**
     * Lie un compte Google à ce compte FLICK (liaison explicite, V24).
     * Appelé après vérification de l'id_token Google ET authentification
     * FLICK préalable (jamais sur simple correspondance d'email).
     */
    public void linkGoogle(String googleId) {
        this.googleId = googleId;
    }

    /**
     * Rafraîchit le profil visible depuis les claims Google (nom, photo)
     * à chaque connexion Google. Ne touche ni au rôle, ni au statut,
     * ni au mot de passe, ni à l'email (identifiant de connexion FLICK).
     */
    public void updateGoogleProfile(String displayName, String avatarUrl) {
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
    }

    public String getEmail() {
        return email;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public Role getRole() {
        return role;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    /** Identifiant Google lié (claim "sub"), ou NULL si aucun. */
    public String getGoogleId() {
        return googleId;
    }

    /** Méthode de création du compte (LOCAL par défaut, GOOGLE via Google). */
    public AuthProvider getAuthProvider() {
        return authProvider;
    }
}