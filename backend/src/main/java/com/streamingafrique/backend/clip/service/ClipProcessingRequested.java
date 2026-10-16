package com.streamingafrique.backend.clip.service;

/**
 * Demande de traitement d'un clip (étape 15, §36) : mêmes garanties que
 * {@link com.streamingafrique.backend.video.service.VideoProcessingRequested}
 * (traitement APRÈS commit uniquement).
 */
public record ClipProcessingRequested(Long clipId) {
}
