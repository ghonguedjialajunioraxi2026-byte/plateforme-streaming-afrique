package com.streamingafrique.backend.category;

import java.util.HashSet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.category.model.Category;
import com.streamingafrique.backend.category.repository.CategoryRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sous-étape 7.12 : structure extensible des catégories (H2, transaction
 * annulée après chaque test). Aucun seed lancé : le catalogue est vérifié
 * en mémoire uniquement.
 */
@SpringBootTest
@Transactional
class CategoryStructureTest {

    @Autowired
    private CategoryRepository categories;

    @Test
    void creation_defauts_actif_ordre_zero_sans_parent_ni_image() {
        Category cat = categories.save(Category.create("Gaming", "gaming", "Jeux"));

        assertEquals(Category.Status.ACTIVE, cat.getStatus());
        assertEquals(0, cat.getDisplayOrder());
        assertNull(cat.getParent());
        assertNull(cat.getImageUrl());
    }

    @Test
    void sous_categorie_parente_optionnelle() {
        Category jeux = categories.save(Category.create("Jeux", "jeux", "Gaming"));
        Category esport = categories.save(Category.create("Esport", "esport", "Compétition"));
        esport.updateParent(jeux);
        esport.updateDisplay("https://cdn.example.com/esport.png", 3);
        categories.saveAndFlush(esport);

        Category lu = categories.findBySlug("esport").orElseThrow();
        assertEquals(jeux.getId(), lu.getParent().getId());
        assertEquals("https://cdn.example.com/esport.png", lu.getImageUrl());
        assertEquals(3, lu.getDisplayOrder());

        lu.updateParent(null);
        categories.saveAndFlush(lu);
        assertNull(categories.findBySlug("esport").orElseThrow().getParent());
    }

    @Test
    void archivage_reversible() {
        Category sport = categories.save(Category.create("Sport", "sport", "Foot"));
        sport.changeStatus(Category.Status.ARCHIVED);
        categories.saveAndFlush(sport);
        assertEquals(Category.Status.ARCHIVED, categories.findBySlug("sport").orElseThrow().getStatus());

        sport.changeStatus(Category.Status.ACTIVE);
        categories.saveAndFlush(sport);
        assertEquals(Category.Status.ACTIVE, categories.findBySlug("sport").orElseThrow().getStatus());
    }

    @Test
    void catalogue_initial_coherent_douze_entrees_slugs_uniques() {
        assertEquals(12, CategoryCatalog.INITIAL.size());
        var slugs = new HashSet<String>();
        var ordres = new HashSet<Integer>();
        for (var entry : CategoryCatalog.INITIAL) {
            assertTrue(slugs.add(entry.slug()), "slug dupliqué : " + entry.slug());
            assertTrue(ordres.add(entry.displayOrder()), "ordre dupliqué : " + entry.displayOrder());
        }
    }
}
