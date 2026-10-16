package com.streamingafrique.backend.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stratégie de tests (étape 6.29) — test UNITAIRE pur.
 *
 * Aucun contexte Spring, aucune base : la méthode est déterministe, le test
 * répond en millisecondes. C'est l'outil adapté aux utilitaires partagés
 * (ici les slugs d'URL des catégories, chaînes et contenus) : on verrouille
 * les règles de normalisation une fois pour toutes.
 */
class SlugUtilTest {

    @ParameterizedTest(name = "\"{0}\" → \"{1}\"")
    @CsvSource({
            "Jeux Vidéo,              jeux-video",
            "Tchad & Cameroun,        tchad-cameroun",
            "Étapes & Astuces,        etapes-astuces",
            "  Espaces en trop  ,     espaces-en-trop",
            "Déjà--Tirets,            deja-tirets",
            "UPPERCASE,               uppercase",
            "Abidjan 2026 !,          abidjan-2026",
            "'',                      ''"
    })
    @DisplayName("Normalisation : accents, casse, ponctuation et espaces")
    void normalisation(String input, String expected) {
        assertThat(SlugUtil.slugify(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("Caractères non latins seuls → slug vide (jamais null, jamais d'exception)")
    void caracteres_non_latins() {
        assertThat(SlugUtil.slugify("日本語")).isEmpty();
    }

    @Test
    @DisplayName("Déterminisme : même entrée → même sortie (slugs stables pour les URLs)")
    void determinisme() {
        assertThat(SlugUtil.slugify("Direct Coupe d'Afrique"))
                .isEqualTo(SlugUtil.slugify("Direct Coupe d'Afrique"));
    }
}
