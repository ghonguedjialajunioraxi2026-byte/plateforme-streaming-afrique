package com.streamingafrique.backend.video.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.streamingafrique.backend.clip.service.ClipProcessingRequested;

/**
 * Déclenche le traitement média APRÈS commit (étape 15, §14/§36).
 *
 * Pourquoi après commit : le worker tourne dans son thread et sa
 * transaction — soumis DANS la requête, il pourrait lire avant la
 * visibilité de la ligne (clip/VOD coincé en PROCESSING pour toujours).
 * Ici, l'événement Spring n'est consommé qu'au commit réel ; en cas de
 * rollback (dont les tests {@code @Transactional}), RIEN ne part.
 */
@Component
public class MediaProcessingListener {

    private static final Logger log = LoggerFactory.getLogger(MediaProcessingListener.class);

    private final MediaProcessingWorker processing;

    public MediaProcessingListener(MediaProcessingWorker processing) {
        this.processing = processing;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVideoRequested(VideoProcessingRequested requested) {
        log.info("Traitement VOD demandé (id={})", requested.videoId());
        processing.processVideoAsync(requested.videoId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onClipRequested(ClipProcessingRequested requested) {
        log.info("Traitement clip demandé (id={})", requested.clipId());
        processing.processClipAsync(requested.clipId());
    }
}
