package com.streamingafrique.backend.video.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.streamingafrique.backend.category.model.Category;
import com.streamingafrique.backend.common.model.BaseEntity;
import com.streamingafrique.backend.creator.model.CreatorProfile;

/**
 * Entité "Video" → table {@code videos}.
 *
 * RÔLE : contenu vidéo à la demande (VOD) : replay d'un direct ou
 * vidéo téléversée directement. Complémentaire du direct : le viewer
 * consomme le direct en temps réel, la vidéo quand il veut.
 *
 * La date de publication (publishedAt) est gérée séparément de la date
 * de création (audit) : une vidéo peut être créée en brouillon puis
 * publiée plus tard.
 *
 * Fichiers (sous-étape 7.14) : AUCUN blob en PostgreSQL. Seules des
 * références vers le stockage objet/CDN futur : {@code storageKey} (clé
 * stable dans le bucket) + {@code videoUrl} (URL de diffusion, possiblement
 * signée à durée courte). La miniature suit la même règle.
 *
 * Vues : AUCUN compteur stocké (même décision qu'en 7.11 : contention en
 * écriture). Les vues se calculent via watch_history + stat_snapshots.
 *
 * Relations unidirectionnelles @ManyToOne (LAZY) : créateur = la chaîne
 * (jamais null) + catégorie (optionnelle) + stream d'origine (optionnel).
 * Pas de collection inverse → pas de boucle JSON.
 */
@Entity
@Table(name = "videos", indexes = {
        @Index(name = "idx_videos_creator", columnList = "creator_id"),
        @Index(name = "idx_videos_creator_visibility_published",
                columnList = "creator_id,visibility,publishedAt"),
        @Index(name = "idx_videos_category", columnList = "category_id"),
        @Index(name = "idx_videos_origin_stream", columnList = "originStream"),
        @Index(name = "idx_videos_published_at", columnList = "published_at")
})
public class Video extends BaseEntity {

    /** Qui peut voir la vidéo. */
    public enum Visibility {
        PUBLIC, UNLISTED, PRIVATE
    }

    /** État du traitement du fichier (upload → encodage → disponible). */
    public enum ProcessingStatus {
        UPLOADED, PROCESSING, READY, FAILED
    }

    @Column(nullable = false, length = 150)
    private String title;

    @Column(length = 2000)
    private String description;

    /** Le téléverseur (= la chaîne) — jamais null. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creator_id", nullable = false)
    private CreatorProfile creator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    /**
     * Stream d'origine (replay) — NULL = téléversement direct.
     * Un stream supprimé/masqué n'efface jamais la VOD (RESTRICT + logique).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_stream_id")
    private com.streamingafrique.backend.stream.model.Stream originStream;

    /** URL de diffusion (CDN, /uploads/…, possiblement signée) — jamais un blob. */
    @Column(length = 500)
    private String videoUrl;

    /**
     * Clé stable dans le bucket objet futur (ex. "videos/42/source.mp4").
     * NULL tant que le stockage objet n'est pas branché.
     */
    @Column(length = 500)
    private String storageKey;

    @Column(length = 500)
    private String thumbnailUrl;

    /** Durée totale en secondes. */
    @Column(nullable = false)
    private int durationSeconds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Visibility visibility;

    /** Traitement par défaut : disponible (comportement historique conservé). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProcessingStatus processingStatus = ProcessingStatus.READY;

    /** Masquage logique (jamais de DELETE physique avec interactions). */
    @Column(nullable = false)
    private boolean deleted = false;

    /** Moment où la vidéo devient visible — null tant qu'elle est en brouillon. */
    private Instant publishedAt;

    /**
     * Langue déclarée du contenu (ex. "fr", "en", "wo") — NULL = non
     * renseignée. Déclarative (jamais bloquante, jamais devinée).
     */
    @Column(length = 10)
    private String language;

    /**
     * Compteur de vues PERSISTÉ (étape 15) : incrémenté atomiquement en base
     * ({@code view_count = view_count + 1}, jamais de lecture-modification en
     * mémoire) uniquement après le garde anti-abus du service (un viewer =
     * une vue par fenêtre). Les feeds cachés peuvent le montrer avec retard ;
     * le détail est toujours exact.
     */
    @Column(nullable = false)
    private long viewCount = 0;

    /** Constructeur vide requis par JPA (ne pas utiliser autrement). */
    protected Video() {
    }

    /** Fabrique : publie une vidéo immédiatement (téléversement direct). */
    public static Video publish(CreatorProfile creator, String title, String description, Category category,
                                String videoUrl, String thumbnailUrl, int durationSeconds) {
        if (videoUrl == null || videoUrl.isBlank()) {
            throw new IllegalArgumentException("L'URL de la vidéo est obligatoire (publication manuelle)");
        }
        Video video = new Video();
        video.creator = creator;
        video.title = title;
        video.description = description;
        video.category = category;
        video.videoUrl = videoUrl;
        video.thumbnailUrl = thumbnailUrl;
        video.durationSeconds = durationSeconds;
        video.visibility = Visibility.PUBLIC;
        video.processingStatus = ProcessingStatus.READY;
        video.deleted = false;
        video.publishedAt = Instant.now();
        return video;
    }

    /** Fabrique : replay issu d'un stream (origine tracée). */
    public static Video fromStream(CreatorProfile creator, com.streamingafrique.backend.stream.model.Stream origin,
                                   String title, String description, String videoUrl, int durationSeconds) {
        Video video = publish(creator, title, description, null, videoUrl, null, durationSeconds);
        video.originStream = origin;
        return video;
    }

    /**
     * Fabrique : VOD AUTOMATIQUE à la fin d'un live (étape 15, §12-13).
     * Naît en PROCESSING et en brouillon (pas de publication fantôme) : le
     * worker la passe READY + publiée quand le fichier est vérifié. Durée =
     * fin - début du live (≥ 1 s). URL NULL si aucun enregistrement (le
     * worker la marquera FAILED, jamais READY sans fichier).
     */
    public static Video autoFromEndedStream(CreatorProfile creator,
                                            com.streamingafrique.backend.stream.model.Stream origin,
                                            String videoUrl, int durationSeconds) {
        if (creator == null || origin == null) {
            throw new IllegalArgumentException("Chaîne et stream d'origine obligatoires");
        }
        Video video = new Video();
        video.creator = creator;
        video.title = origin.getTitle();
        video.description = origin.getDescription();
        video.category = origin.getCategory();
        video.videoUrl = videoUrl;
        video.thumbnailUrl = origin.getThumbnailUrl();
        video.durationSeconds = Math.max(1, durationSeconds);
        video.visibility = Visibility.PUBLIC;
        video.processingStatus = ProcessingStatus.PROCESSING;
        video.deleted = false;
        video.publishedAt = null;
        video.originStream = origin;
        // Hérite la langue déclarée du live (jamais devinée, NULL accepté).
        video.language = origin.getLanguage();
        return video;
    }

    /** Mise à jour du contenu éditable (la videoUrl reste immuable). */
    public void updateContent(String title, String description, Category category,
                              String thumbnailUrl, int durationSeconds) {
        this.title = title;
        this.description = description;
        this.category = category;
        this.thumbnailUrl = thumbnailUrl;
        this.durationSeconds = durationSeconds;
    }

    /** Langue déclarée (normalisée minuscule, NULL accepté). */
    public void setLanguage(String language) {
        if (language == null || language.isBlank()) {
            this.language = null;
            return;
        }
        String norm = language.trim().toLowerCase(java.util.Locale.ROOT);
        if (!norm.matches("[a-z]{2,3}")) {
            throw new IllegalArgumentException("Langue invalide (2-3 lettres)");
        }
        this.language = norm;
    }

    /** Changement de visibilité (feed public n'expose que PUBLIC). */
    public void changeVisibility(Visibility visibility) {
        this.visibility = visibility;
    }

    /** Avancement du traitement (UPLOADED → PROCESSING → READY / FAILED). */
    public void changeProcessingStatus(ProcessingStatus processingStatus) {
        this.processingStatus = processingStatus;
    }

    /** Publication d'un brouillon (publishedAt NULL → maintenant). */
    public void markPublished() {
        if (this.publishedAt == null) {
            this.publishedAt = Instant.now();
        }
    }

    /** Masquage logique (modération, retrait). */
    public void markDeleted() {
        this.deleted = true;
    }

    /** Restauration après masquage (les clips désactivés restent à réactiver). */
    public void markRestored() {
        this.deleted = false;
    }

    /** Rattache la référence objet/CDN future (clé stable + URL de diffusion). */
    public void attachSource(String storageKey, String videoUrl) {
        this.storageKey = storageKey;
        this.videoUrl = videoUrl;
    }

    /** Une vue validée par le garde anti-abus (incrément atomique en base). */
    public void incrementView() {
        this.viewCount++;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public CreatorProfile getCreator() {
        return creator;
    }

    public Category getCategory() {
        return category;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public ProcessingStatus getProcessingStatus() {
        return processingStatus;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public com.streamingafrique.backend.stream.model.Stream getOriginStream() {
        return originStream;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public long getViewCount() {
        return viewCount;
    }

    public String getLanguage() {
        return language;
    }
}