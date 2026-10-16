package com.streamingafrique.backend.video.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.clip.model.Clip;
import com.streamingafrique.backend.clip.repository.ClipRepository;
import com.streamingafrique.backend.video.model.Video;
import com.streamingafrique.backend.video.repository.VideoRepository;

/**
 * Rejeu au boot des traitements orphelins (étape 15, §14/§36).
 *
 * Un crash/redéploiement entre la création (PROCESSING) et la fin du worker
 * laisse des lignes coincées : au démarrage, tout {@code UPLOADED} ou
 * {@code PROCESSING} est retraité SYNCHRONEMENT (volumes faibles, chaque
 * passage rapide sans FFmpeg ; le worker reste idempotent : READY ignoré,
 * supprimés ignorés). Après commit du boot, le cycle normal (événement
 * post-commit → async) reprend.
 *
 * Désactivable ({@code app.video.recovery-enabled=false}, les tests
 * appellent {@link #recover()} à la main).
 */
@Component
@Order(100)
@ConditionalOnProperty(name = "app.video.recovery-enabled", havingValue = "true", matchIfMissing = true)
public class MediaProcessingRecovery implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MediaProcessingRecovery.class);

    private final VideoRepository videos;
    private final ClipRepository clips;
    private final MediaProcessingWorker processing;

    public MediaProcessingRecovery(VideoRepository videos, ClipRepository clips,
                                   MediaProcessingWorker processing) {
        this.videos = videos;
        this.clips = clips;
        this.processing = processing;
    }

    @Override
    public void run(ApplicationArguments args) {
        int replayed = recover();
        if (replayed > 0) {
            log.info("Rejeu au boot : {} traitement(s) orphelin(s) retraité(s)", replayed);
        }
    }

    /**
     * Retraite les orphelins (VOD puis clips). Retourne le nombre de
     * soumissions (observabilité) — les sans-fichier passeront FAILED
     * avec notification, comme en cycle normal.
     */
    @Transactional
    public int recover() {
        int count = 0;
        List<Video> stuckVideos = videos.findByProcessingStatus(Video.ProcessingStatus.PROCESSING);
        stuckVideos.addAll(videos.findByProcessingStatus(Video.ProcessingStatus.UPLOADED));
        for (Video video : stuckVideos) {
            if (!video.isDeleted()) {
                processing.processVideoNow(video.getId());
                count++;
            }
        }
        List<Clip> stuckClips = clips.findByProcessingStatus(Clip.ProcessingStatus.PROCESSING);
        stuckClips.addAll(clips.findByProcessingStatus(Clip.ProcessingStatus.UPLOADED));
        for (Clip clip : stuckClips) {
            if (!clip.isDeleted()) {
                processing.processClipNow(clip.getId());
                count++;
            }
        }
        return count;
    }
}
