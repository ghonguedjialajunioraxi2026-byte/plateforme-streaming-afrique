package com.streamingafrique.backend.video.service;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.clip.model.Clip;
import com.streamingafrique.backend.clip.repository.ClipRepository;
import com.streamingafrique.backend.event.EventType;
import com.streamingafrique.backend.event.service.StreamEventPublisher;
import com.streamingafrique.backend.media.storage.VideoStorage;
import com.streamingafrique.backend.notification.model.Notification;
import com.streamingafrique.backend.notification.service.NotificationService;
import com.streamingafrique.backend.video.model.Video;
import com.streamingafrique.backend.video.repository.VideoRepository;

/**
 * Traitement média ASYNCHRONE (étape 15, §14) — JAMAIS sur le thread HTTP.
 *
 * Aujourd'hui (sans FFmpeg) : vérification déclarative — le fichier référencé
 * existe-t-il (clé locale) ou l'URL est-elle fournie (CDN) ? Oui → READY +
 * publication + notification + événement WS ; non → FAILED + notification
 * d'erreur. Quand {@code app.video.ffmpeg-enabled=true} + binaire présent,
 * la durée réelle et la miniature sont extraites avant READY (même pipeline,
 * mêmes événements, mêmes garanties).
 *
 * Testabilité : le cœur synchrone ({@code processVideoNow/processClipNow},
 * {@code @Transactional}) est appelé directement par les tests ; les
 * enveloppes {@code @Async} servent la production (requêtes non bloquées).
 */
@Component
public class MediaProcessingWorker {

    private static final Logger log = LoggerFactory.getLogger(MediaProcessingWorker.class);

    private final VideoRepository videos;
    private final ClipRepository clips;
    private final VideoStorage storage;
    private final ObjectProvider<FfmpegProbe> ffmpeg;
    private final StreamEventPublisher events;
    private final NotificationService notifications;

    public MediaProcessingWorker(VideoRepository videos, ClipRepository clips,
                                 VideoStorage storage, ObjectProvider<FfmpegProbe> ffmpeg,
                                 StreamEventPublisher events,
                                 NotificationService notifications) {
        this.videos = videos;
        this.clips = clips;
        this.storage = storage;
        this.ffmpeg = ffmpeg;
        this.events = events;
        this.notifications = notifications;
    }

    /**
     * Production : traite une VOD hors requête HTTP.
     *
     * Transaction OBLIGATOIRE ici (l'appel interne vers {@code processVideoNow}
     * ne traverse pas le proxy : sans elle, aucun lazy ne chargerait).
     */
    @Async
    @Transactional
    public void processVideoAsync(Long videoId) {
        try {
            processVideoNow(videoId);
        } catch (Exception e) {
            log.error("Traitement VOD {} : échec inattendu", videoId, e);
        }
    }

    /** Production : traite un clip hors requête HTTP (même règle). */
    @Async
    @Transactional
    public void processClipAsync(Long clipId) {
        try {
            processClipNow(clipId);
        } catch (Exception e) {
            log.error("Traitement clip {} : échec inattendu", clipId, e);
        }
    }

    /** Cœur synchrone VOD (transaction dédiée, rejouable). */
    @Transactional
    public void processVideoNow(Long videoId) {
        Video video = videos.findById(videoId).orElse(null);
        if (video == null || video.isDeleted()) {
            return;
        }
        if (video.getProcessingStatus() == Video.ProcessingStatus.READY) {
            return;
        }
        boolean fileAvailable = isFileAvailable(video.getStorageKey(), video.getVideoUrl());
        if (!fileAvailable) {
            video.changeProcessingStatus(Video.ProcessingStatus.FAILED);
            videos.save(video);
            events.publish(EventType.VOD_FAILED, Map.of("videoId", video.getId()));
            notifications.notify(video.getCreator().getUser(), Notification.Type.SYSTEM,
                    "Vidéo indisponible",
                    "« " + video.getTitle() + " » : aucun fichier — vérifiez l'enregistrement",
                    "/creator/videos");
            log.warn("VOD {} FAILED : aucun fichier (ni clé locale ni URL)", video.getId());
            return;
        }
        ffmpeg.ifAvailable(probe -> probe.probeDurationSeconds(video.getStorageKey())
                .ifPresent(seconds -> {
                    if (seconds > 0) {
                        video.updateContent(video.getTitle(), video.getDescription(),
                                video.getCategory(), video.getThumbnailUrl(), seconds);
                    }
                }));
        video.changeProcessingStatus(Video.ProcessingStatus.READY);
        video.markPublished();
        videos.save(video);
        events.publish(EventType.VOD_READY, Map.of("videoId", video.getId(),
                "creatorId", video.getCreator().getId()));
        notifications.notify(video.getCreator().getUser(), Notification.Type.NEW_VIDEO,
                "Votre vidéo est disponible",
                "« " + video.getTitle() + " » est maintenant lisible",
                "/video/" + video.getId());
        log.info("VOD {} READY (chaîne {})", video.getId(), video.getCreator().getId());
    }

    /** Cœur synchrone clip (transaction dédiée, rejouable). */
    @Transactional
    public void processClipNow(Long clipId) {
        Clip clip = clips.findById(clipId).orElse(null);
        if (clip == null || clip.isDeleted()) {
            return;
        }
        if (clip.getProcessingStatus() == Clip.ProcessingStatus.READY) {
            return;
        }
        if (!sourceAvailable(clip)) {
            clip.changeProcessingStatus(Clip.ProcessingStatus.FAILED);
            clips.save(clip);
            events.publish(EventType.CLIP_FAILED, Map.of("clipId", clip.getId()));
            notifyClipOwner(clip, "Clip indisponible",
                    "« " + clip.getTitle() + " » : source retirée ou fichier manquant");
            log.warn("Clip {} FAILED : source indisponible", clip.getId());
            return;
        }
        clip.changeProcessingStatus(Clip.ProcessingStatus.READY);
        clips.save(clip);
        events.publish(EventType.CLIP_READY, Map.of("clipId", clip.getId(),
                "creatorId", clip.getCreator().getId()));
        notifyClipOwner(clip, "Votre clip est prêt",
                "« " + clip.getTitle() + " » est maintenant lisible");
        log.info("Clip {} READY (chaîne {})", clip.getId(), clip.getCreator().getId());
    }

    private boolean isFileAvailable(String storageKey, String url) {
        if (storageKey != null && !storageKey.isBlank()) {
            return storage.exists(storageKey);
        }
        return url != null && !url.isBlank();
    }

    private boolean sourceAvailable(Clip clip) {
        if (!isFileAvailable(clip.getStorageKey(), clip.getClipUrl())) {
            return false;
        }
        if (clip.getVideo() != null) {
            return !clip.getVideo().isDeleted()
                    && clip.getVideo().getProcessingStatus() == Video.ProcessingStatus.READY;
        }
        return clip.getStream() != null;
    }

    private void notifyClipOwner(Clip clip, String title, String message) {
        if (clip.getCreatedBy() != null) {
            notifications.notify(clip.getCreatedBy(), Notification.Type.NEW_CLIP, title, message,
                    "/clip/" + clip.getId());
        } else {
            notifications.notify(clip.getCreator().getUser(), Notification.Type.NEW_CLIP,
                    title, message, "/clip/" + clip.getId());
        }
    }
}
