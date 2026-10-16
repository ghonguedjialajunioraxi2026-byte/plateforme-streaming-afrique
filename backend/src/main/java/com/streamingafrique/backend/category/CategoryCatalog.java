package com.streamingafrique.backend.category;

import java.util.List;

/**
 * Catalogue initial des catégories (sous-étape 7.12) — PRÉPARÉ, NON EXÉCUTÉ.
 *
 * Aucun seed n'est lancé : ni {@code CommandLineRunner}, ni migration, ni
 * appel depuis un service. Le jour du seed (sous-étape dédiée, sur confirmation),
 * chaque entrée sera créée via {@code CategoryService.create} (unicité nom/slug
 * vérifiée) dans l'ordre {@code displayOrder}, avec images téléversées ensuite
 * via {@code /api/uploads} (ici {@code imageUrl = null}).
 *
 * 12 catégories : Gaming, Musique, Sport, Discussion, Podcast, Éducation,
 * Technologie, Art, IRL, Cuisine, Business +243 (ancrage Afrique : entrepreneuriat
 * local, contenu en langues nationales).
 */
public final class CategoryCatalog {

    /** Une entrée du catalogue initial (sans image : téléversement ultérieur). */
    public record Entry(String name, String slug, String description, int displayOrder) {}

    /** Catalogue ordonné d'affichage (0 = tête). Slugs uniques, stables. */
    public static final List<Entry> INITIAL = List.of(
            new Entry("Gaming", "gaming", "Jeux vidéo, esport et speedrun", 0),
            new Entry("Musique", "musique", "Concerts, clips et sessions live", 1),
            new Entry("Sport", "sport", "Football africain, basket, combat et analyses", 2),
            new Entry("Discussion", "discussion", "Talk-shows et débats en direct", 3),
            new Entry("Podcast", "podcast", "Émissions audio et vidéo à la demande", 4),
            new Entry("Éducation", "education", "Cours, tutoriels et formation", 5),
            new Entry("Technologie", "technologie", "Dev, IA, gadgets et startups", 6),
            new Entry("Art", "art", "Peinture, danse, mode et création", 7),
            new Entry("IRL", "irl", "Vie réelle, voyages et street content", 8),
            new Entry("Cuisine", "cuisine", "Recettes africaines et street-food", 9),
            new Entry("Business", "business", "Entrepreneuriat et e-commerce", 10),
            new Entry("+243", "plus243", "Contenus RDC et diaspora : rumba, humour, culture", 11));

    private CategoryCatalog() {
    }
}
