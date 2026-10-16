package com.streamingafrique.backend.video.dto;

/**
 * URLS de lecture d'une vidéo ou d'un clip (étape 15, §17/§22).
 *
 * Seules des URLs de diffusion (jamais de chemin interne, §19 : la clé
 * logique {@code storageKey} ne sort JAMAIS). Aujourd'hui : fichier direct
 * (MP4/WebM) ; demain : manifestes HLS/ABR via les mêmes champs
 * (compatibilité ascendante : le player choisit le meilleur supporté).
 */
public record PlaybackResponse(
        /** URL de lecture principale (fichier MP4/WebM ou manifeste). */
        String playbackUrl,
        /** Variante HLS (manifeste master.m3u8) — NULL si indisponible. */
        String hlsUrl,
        /** Miniature d'affiche. */
        String thumbnailUrl,
        /** Durée totale (secondes). */
        int durationSeconds,
        /** Début imposé (clip : position dans la source, 0 pour une VOD). */
        int startsAtSeconds
) {
}
