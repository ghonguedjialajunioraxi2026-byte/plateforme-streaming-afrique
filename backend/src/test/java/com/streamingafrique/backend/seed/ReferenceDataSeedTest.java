package com.streamingafrique.backend.seed;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.streamingafrique.backend.admin.repository.AppConfigRepository;
import com.streamingafrique.backend.category.repository.CategoryRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sous-étape 7.25 : seed de référence — activation par propriété, contenu
 * sûr (12 catégories + 3 paramètres, ZÉRO utilisateur), idempotence au
 * rejeu (H2, transaction de test non committée).
 */
@SpringBootTest(properties = "app.seed.reference-data=true")
class ReferenceDataSeedTest {

    @Autowired
    private ReferenceDataSeeder seeder;
    @Autowired
    private CategoryRepository categories;
    @Autowired
    private AppConfigRepository configs;

    @Test
    void seedInsereReferenceSansUtilisateurEtResteIdempotent() {
        // Le runner a déjà tourné au démarrage du contexte.
        assertEquals(12, categories.count());
        assertTrue(categories.existsBySlug("gaming"));
        assertTrue(categories.existsBySlug("plus243"));
        assertEquals(3, configs.count());
        assertTrue(configs.findByConfigKeyAndVersion("storage.max-image-mb", 1).isPresent());

        // Rejeu : aucun doublon (ni catégories, ni paramètres).
        seeder.seed();
        assertEquals(12, categories.count());
        assertEquals(3, configs.count());
    }
}
