package com.streamingafrique.backend.clip.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Check;

import com.streamingafrique.backend.common.model.BaseEntity;
import com.streamingafrique.backend.creator.model.CreatorProfile;
import com.streamingafrique.backend.stream.model.Stream;
import com.streamingafrique.backend.video.model.Video;

/**
 * Entité "Clip" → table {@code clips}.
 *
 * RÔLE : extrait vidéo court (quelques secondes/minutes) — le format
 * VIRAL de la plateforme. Un clip naît D'UN direct {@link Stream} OU
 * D'UNE vidéo {@link Video} : la contrainte @Check XOR garantit qu'il en a
 * bien UNE seule source (jamais les deux, jamais aucune — resserrement
 * 7.15, l'ancien OR autorisait les deux à la fois).
 *
 * Auteur (sous-étape 7.15 « appartenir à un utilisateur ») : l'auteur est
 * la CHAÎNE ({@code creator_id}), elle-même rattachée à exactement un
 * compte — pas de seconde FK vers users (duplication et risque de
 * divergence auteur/propriétaire).
 *
 * Fichiers : AUCUN blob en base — références objet/CDN comme les vidéos.
 * Vues : AUCUN compteur stocké (même règle qu'en 7.11/7.14 : contention) —
 * via watch_history + stat_snapshots.
 *
 * Partage public : {@code shareSlug} (lien court /c/xxxxxxxxxx), unique,
 * régénérable, prêt pour le partage hors plateforme.
 *
 * Relations unidirectionnelles @ManyToOne (LAZY) : créateur (jamais null,
 * l'auteur du clip) + la source du clip (XOR, l'une des deux).
 */
@Entity
@Table(name = "clips", indexes = {
        @Index(name = "idx_clips_creator", columnList = "creator_id"),
        @Index(name = "idx_clips_creator_visibility", columnList = "creator_id,visibility"),
        @Index(name = "idx_clips_stream", columnList = "stream_id"),
        @Index(name = "idx_clips_video", columnList = "video_id"),
        @Index(name = "uk_clips_share_slug", columnList = "shareSlug", unique = true)
})
@Check(constraints = "(stream_id IS NULL) <> (video_id IS NULL)")
public class Clip extends BaseEntity {

    /** Qui peut voir le clip. */
    public enum Visibility {
        PUBLIC, UNLISTED, PRIVATE
    }

    /** État du traitement du fichier (upload → encodage → disponible). */
    public enum ProcessingStatus {
        UPLOADED, PROCESSING, READY, FAILED
    }

    @Column(nullable = false, length = 150)
    private String title;

    /** L'auteur du clip (= la chaîne) — jamais null. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creator_id", nullable = false)
    private CreatorProfile creator;

    /**
     * Compte ayant DÉCOUPÉ le clip (étape 15, §32/§48) — NULL pour les clips
     * historiques (créés par la chaîne avant cette distinction). Distinction
     * conservée pour toujours : modération, revenus futurs, suppressions.
     * {@code creator} = chaîne source (propriétaire du contenu),
     * {@code createdBy} = auteur du découpage.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private com.streamingafrique.backend.user.model.User createdBy;

    /** Source possible 1 : le direct. XOR — l'une des deux, jamais les deux. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stream_id")
    private Stream stream;

    /** Source possible 2 : la vidéo. XOR — l'une des deux, jamais les deux. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id")
    private Video video;

    /** URL de diffusion (CDN, possiblement signée) — jamais un blob. */
    @Column(nullable = false, length = 500)
    private String clipUrl;

    /**
     * Clé stable dans le bucket objet futur (ex. "clips/7/source.mp4").
     * NULL tant que le stockage objet n'est pas branché.
     */
    @Column(length = 500)
    private String storageKey;

    @Column(length = 500)
    private String thumbnailUrl;

    /** Position de l'extrait dans la source, en secondes (>= 0). */
    @Column(nullable = false)
    private int startsAtSeconds = 0;

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

    /**
     * Compteur de vues PERSISTÉ (étape 15) : incrémenté atomiquement en base
     * après le garde anti-abus du service (même règle que les VOD).
     */
    @Column(nullable = false)
    private long viewCount = 0;

    /**
     * Jeton de partage public (/c/{shareSlug}) — unique, régénérable.
     * Suit le sort de la visibilité : un clip PRIVATE ne se partage pas
     * (contrôle applicatif, pas de CHECK inter-colonnes).
     */
    @Column(nullable = false, unique = true, length = 20)
    private String shareSlug;

    /** Constructeur vide requis par JPA (ne pas utiliser autrement). */
    protected Clip() {
    }

    /** Fabrique : crée un clip à partir d'un DIRECT. */
    public static Clip fromStream(CreatorProfile creator, Stream stream, String title,
                                  String clipUrl, int durationSeconds) {
        Clip clip = fromCreator(creator, title, clipUrl, durationSeconds);
        clip.stream = stream;
        return clip;
    }

    /** Fabrique : crée un clip à partir d'une VIDÉO. */
    public static Clip fromVideo(CreatorProfile creator, Video video, String title,
                                  String clipUrl, int durationSeconds) {
        Clip clip = fromCreator(creator, title, clipUrl, durationSeconds);
        clip.video = video;
        return clip;
    }

    /**
     * Fabrique : segment validé d'une vidéo (étape 15, §34) — position et
     * durée vérifiées par le service AVANT l'appel (bornes de la source,
     * durée maximale configurable). L'URL réutilise le fichier source :
     * aucun ré-encodage sans FFmpeg (découpe serveur future).
     */
    public static Clip segmentOfVideo(CreatorProfile sourceChannel,
                                      com.streamingafrique.backend.user.model.User cutBy,
                                      Video video, String title, String clipUrl,
                                      int startsAtSeconds, int durationSeconds,
                                      Visibility visibility) {
        if (sourceChannel == null || video == null) {
            throw new IllegalArgumentException("Chaîne source et vidéo obligatoires");
        }
        if (startsAtSeconds < 0 || durationSeconds <= 0) {
            throw new IllegalArgumentException("Segment invalide (début >= 0, durée > 0)");
        }
        Clip clip = fromCreator(sourceChannel, title, clipUrl, durationSeconds);
        clip.video = video;
        clip.startsAtSeconds = startsAtSeconds;
        clip.visibility = visibility;
        clip.processingStatus = ProcessingStatus.PROCESSING;
        clip.createdBy = cutBy;
        return clip;
    }

    /**
     * Fabrique : segment validé d'un direct (étape 15, §33) — mêmes règles
     * que {@link #segmentOfVideo}. Sans enregistrement disponible, le worker
     * marquera FAILED (jamais READY sans fichier).
     */
    public static Clip segmentOfStream(CreatorProfile sourceChannel,
                                       com.streamingafrique.backend.user.model.User cutBy,
                                       Stream stream, String title, String clipUrl,
                                       int startsAtSeconds, int durationSeconds,
                                       Visibility visibility) {
        if (sourceChannel == null || stream == null) {
            throw new IllegalArgumentException("Chaîne source et direct obligatoires");
        }
        if (startsAtSeconds < 0 || durationSeconds <= 0) {
            throw new IllegalArgumentException("Segment invalide (début >= 0, durée > 0)");
        }
        Clip clip = fromCreator(sourceChannel, title, clipUrl, durationSeconds);
        clip.stream = stream;
        clip.startsAtSeconds = startsAtSeconds;
        clip.visibility = visibility;
        clip.processingStatus = ProcessingStatus.PROCESSING;
        clip.createdBy = cutBy;
        return clip;
    }

    private static Clip fromCreator(CreatorProfile creator, String title, String clipUrl, int durationSeconds) {
        Clip clip = new Clip();
        clip.creator = creator;
        clip.title = title;
        clip.clipUrl = clipUrl;
        clip.durationSeconds = durationSeconds;
        clip.visibility = Visibility.PUBLIC;
        clip.processingStatus = ProcessingStatus.READY;
        clip.deleted = false;
        clip.shareSlug = generateShareSlug();
        return clip;
    }

    /** Jeton URL-safe 10 caractères (partage public, unicité en base). */
    private static String generateShareSlug() {
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    /** Pose la miniature (fournie séparément après la fabrique). */
    public void setThumbnail(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    /** Mise à jour du contenu éditable. */
    public void updateContent(String title, String thumbnailUrl, int durationSeconds) {
        this.title = title;
        this.thumbnailUrl = thumbnailUrl;
        this.durationSeconds = durationSeconds;
    }

    /** Changement de visibilité (feed public n'expose que PUBLIC). */
    public void changeVisibility(Visibility visibility) {
        this.visibility = visibility;
    }

    /** Avancement du traitement (UPLOADED → PROCESSING → READY / FAILED). */
    public void changeProcessingStatus(ProcessingStatus processingStatus) {
        this.processingStatus = processingStatus;
    }

    /** Position de l'extrait dans la source. */
    public void updateSegment(int startsAtSeconds) {
        this.startsAtSeconds = startsAtSeconds;
    }

    /** Masquage logique (modération, retrait). */
    public void markDeleted() {
        this.deleted = true;
    }

    /** Restauration après masquage. */
    public void markRestored() {
        this.deleted = false;
    }

    /**
     * Compteur de vues PERSISTÉ (étape 15) : mêmes règles que les VOD
     * (incrément atomique en base après garde anti-abus du service).
     */
    public void incrementView() {
        this.viewCount++;
    }

    /** Rattache la référence objet/CDN future (clé stable + URL de diffusion). */
    public void attachSource(String storageKey, String clipUrl) {
        this.storageKey = storageKey;
        this.clipUrl = clipUrl;
    }

    /** Régénère le lien de partage (révocation de l'ancien). */
    public void rotateShareSlug() {
        this.shareSlug = generateShareSlug();
    }

    public String getTitle() {
        return title;
    }

    public CreatorProfile getCreator() {
        return creator;
    }

    public Stream getStream() {
        return stream;
    }

    public Video getVideo() {
        return video;
    }

    public String getClipUrl() {
        return clipUrl;
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

    public int getStartsAtSeconds() {
        return startsAtSeconds;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getShareSlug() {
        return shareSlug;
    }

    public com.streamingafrique.backend.user.model.User getCreatedBy() {
        return createdBy;
    }

    public long getViewCount() {
        return viewCount;
    }
}