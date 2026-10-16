package com.streamingafrique.backend.common.util;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Utilitaire de génération de SLUG — partagé par tous les services
 * (catégories, chaînes créateurs, ...).
 *
 * "Jeux Vidéo" → "jeux-video", "Tchad & Cameroun" → "tchad-cameroun".
 */
public final class SlugUtil {

    private SlugUtil() {
    }

    /** Normalise un libellé en slug d'URL, ASCII, minuscules, tirets. */
    public static String slugify(String input) {
        return Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }
}