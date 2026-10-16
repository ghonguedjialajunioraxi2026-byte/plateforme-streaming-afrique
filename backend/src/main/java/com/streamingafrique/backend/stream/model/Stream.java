package com.streamingafrique.backend.stream.model;

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
 * Entité "Stream" → table {@code streams}.
 *
 * RÔLE : un DIRECT (live) — le cœur de la plateforme. Représente la
 * programmation d'un créateur, de la planification jusqu'à la fin du flux.
 *
 * Séparation MÉTIER / TECHNIQUE (sous-étape 7.13) : cette table ne porte
 * que le métier (cycle de vie, métadonnées éditoriales, pic d'audience).
 * Le serveur média (RTMP/WebRTC, sessions d'ingest, viewers instantanés)
 * vit HORS base : seuls une référence secrète (empreinte de clé),
 * le protocole et l'URL de lecture sont conservés ici pour la future
 * intégration. Le compteur instantané de spectateurs reste en Redis ;
 * seul le pic est historisé ({@code peakViewers}).
 *
 * Relations unidirectionnelles @ManyToOne (LAZY) :
 *   - le créateur émetteur = la chaîne (jamais null) ;
 *   - la catégorie (optionnelle pour l'instant).
 * Aucune collection inverse → pas de boucle JSON.
 */
@Entity
@Table(name = "streams", indexes = {
        @Index(name = "idx_streams_creator", columnList = "creator_id"),
        @Index(name = "idx_streams_creator_status", columnList = "creator_id,status"),
        @Index(name = "idx_streams_category", columnList = "category_id"),
        @Index(name = "idx_streams_status", columnList = "status"),
        @Index(name = "idx_streams_status_started", columnList = "status,startedAt"),
        @Index(name = "idx_streams_scheduled_start", columnList = "scheduled_start_at")
})
public class Stream extends BaseEntity {

    /** Cycle de vie d'un direct. */
    public enum Status {
        SCHEDULED, LIVE, ENDED
    }

    /** Visibilité éditoriale (indépendante du statut de diffusion). */
    public enum Visibility {
        PUBLIC, UNLISTED, PRIVATE
    }

    /** Protocole d'ingest du serveur média (futur : RTMP, WebRTC/WHIP). */
    public enum IngestProtocol {
        RTMP, WEBRTC
    }

    @Column(nullable = false, length = 120)
    private String title;

    /** Description éditoriale du direct — NULL = non renseignée. */
    @Column(length = 2000)
    private String description;

    /** Le créateur émetteur (= la chaîne) — jamais null. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creator_id", nullable = false)
    private CreatorProfile creator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    /** Visibilité par défaut : publique. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Visibility visibility = Visibility.PUBLIC;

    /** Miniature du direct — NULL = générée par défaut. */
    @Column(length = 500)
    private String thumbnailUrl;

    /** Langue du direct (ex. "fr", "wo") — NULL = non renseignée. */
    @Column(length = 10)
    private String language;

    /**
     * Pic de spectateurs simultanés (historisé). Le compteur instantané
     * vit en Redis et n'est JAMAIS stocké ici à chaque pulsation.
     */
    @Column(nullable = false)
    private int peakViewers = 0;

    /** Masquage logique (jamais de DELETE physique avec interactions). */
    @Column(nullable = false)
    private boolean deleted = false;

    /**
     * Empreinte (hash) de la clé technique de diffusion — JAMAIS la clé en
     * clair, jamais exposée par l'API. NULL tant que le serveur média n'est
     * pas branché. Rotation future via le service (pas de contrainte d'unicité
     * en base : l'unicité est garantie par le générateur, la colonne porte
     * un index simple pour la résolution ingest).
     */
    @Column(name = "stream_key_hash", length = 128)
    private String streamKeyHash;

    /** Protocole d'ingest retenu — NULL tant que non diffusé. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private IngestProtocol ingestProtocol;

    /** URL de lecture fournie par le serveur média — NULL avant le live. */
    @Column(length = 500)
    private String playbackUrl;

    /** Horaire prévu (renseigné à la programmation). */
    private Instant scheduledStartAt;

    /** Horaire de mise en route effective du flux. */
    private Instant startedAt;

    /** Horaire de fin du direct. */
    private Instant endedAt;

    /** Constructeur vide requis par JPA (ne pas utiliser autrement). */
    protected Stream() {
    }

    /** Fabrique : programme un direct. Statut initial : SCHEDULED. */
    public static Stream schedule(CreatorProfile creator, String title, Category category, Instant scheduledStartAt) {
        Stream stream = new Stream();
        stream.creator = creator;
        stream.title = title;
        stream.category = category;
        stream.scheduledStartAt = scheduledStartAt;
        stream.status = Status.SCHEDULED;
        stream.visibility = Visibility.PUBLIC;
        stream.peakViewers = 0;
        stream.deleted = false;
        return stream;
    }

    /** Transition SCHEDULED → LIVE. */
    public void markStarted() {
        this.status = Status.LIVE;
        this.startedAt = Instant.now();
    }

    /** Transition LIVE → ENDED (seule fin légale d'un direct). */
    public void markEnded() {
        this.status = Status.ENDED;
        this.endedAt = Instant.now();
    }

    /** Réprogramme un direct planifié ou terminé (jamais un LIVE). */
    public void updateSchedule(String title, Category category, Instant scheduledStartAt) {
        this.title = title;
        this.category = category;
        this.scheduledStartAt = scheduledStartAt;
    }

    /** Mise à jour des métadonnées éditoriales (jamais du cycle de vie). */
    public void updateMetadata(String description, String thumbnailUrl, String language, Visibility visibility) {
        this.description = description;
        this.thumbnailUrl = thumbnailUrl;
        this.language = language;
        if (visibility != null) {
            this.visibility = visibility;
        }
    }

    /**
     * Enregistre un pic d'audience (monotone : on ne retient que le max).
     * Appelé par le futur pont stats/Redis, jamais à chaque pulsation.
     */
    public void recordPeakViewers(int viewers) {
        if (viewers > this.peakViewers) {
            this.peakViewers = viewers;
        }
    }

    /** Masquage logique (modération, retrait). */
    public void markDeleted() {
        this.deleted = true;
    }

    /**
     * Rattache les références techniques du serveur média (future intégration
     * RTMP/WebRTC) : empreinte de clé + protocole + URL de lecture.
     */
    public void attachIngest(String streamKeyHash, IngestProtocol ingestProtocol, String playbackUrl) {
        this.streamKeyHash = streamKeyHash;
        this.ingestProtocol = ingestProtocol;
        this.playbackUrl = playbackUrl;
    }

    public String getTitle() {
        return title;
    }

    public CreatorProfile getCreator() {
        return creator;
    }

    public Category getCategory() {
        return category;
    }

    public Status getStatus() {
        return status;
    }

    public String getDescription() {
        return description;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public String getLanguage() {
        return language;
    }

    public int getPeakViewers() {
        return peakViewers;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public String getStreamKeyHash() {
        return streamKeyHash;
    }

    public IngestProtocol getIngestProtocol() {
        return ingestProtocol;
    }

    public String getPlaybackUrl() {
        return playbackUrl;
    }

    public Instant getScheduledStartAt() {
        return scheduledStartAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }
}