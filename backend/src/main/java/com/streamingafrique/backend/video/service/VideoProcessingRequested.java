package com.streamingafrique.backend.video.service;

/**
 * Demande de traitement d'une VOD (étape 15, §14) : publiée en transaction
 * par {@link VideoService}, consommée APRÈS commit par
 * {@link MediaProcessingListener} (jamais de traitement sur une ligne
 * invisible/annulée — pas de READY fantôme, pas de course au commit).
 */
public record VideoProcessingRequested(Long videoId) {
}
