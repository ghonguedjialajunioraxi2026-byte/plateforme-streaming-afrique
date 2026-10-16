package com.streamingafrique.backend.seed;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.admin.model.AppConfig;
import com.streamingafrique.backend.admin.repository.AppConfigRepository;
import com.streamingafrique.backend.category.CategoryCatalog;
import com.streamingafrique.backend.category.model.Category;
import com.streamingafrique.backend.category.repository.CategoryRepository;
import com.streamingafrique.backend.payment.model.CoinPackage;
import com.streamingafrique.backend.payment.repository.CoinPackageRepository;

/**
 * Seed des DONNÉES DE RÉFÉRENCE / SYSTÈME (sous-étape 7.25).
 *
 * Contenu STRICTEMENT sûr, rejouable sans risque :
 *   - catalogue des 12 catégories ({@link CategoryCatalog}, vérifié slug par slug) ;
 *   - 3 paramètres minimaux ({@code app_config}, créés seulement si la clé est absente).
 *
 * Volontairement ABSENT : rôles et permissions (enums + {@code EndpointTier} en
 * code, rien en base), statuts (listes CHECK, rien en base), et surtout AUCUN
 * utilisateur (pas de faux comptes, pas de mot de passe non sécurisé).
 *
 * Séparation (exigée) :
 *   - SYSTÈME → ici (référence sûre) ;
 *   - DÉMONSTRATION → jamais en seed (comptes/contenus de démo = appels API
 *     manuels en dev, traçables, supprimables) ;
 *   - TEST → base H2 des tests, jamais touchée par ce runner.
 *
 * Garde-fous prod : bean créé UNIQUEMENT si
 * {@code app.seed.reference-data=true} (défaut false ; dev=true, prod=false
 * explicite). Chaque insertion vérifie l'existence d'abord → rejouable
 * (redémarrages, multi-instances au boot) sans doublon.
 */
@Component
@ConditionalOnProperty(name = "app.seed.reference-data", havingValue = "true")
public class ReferenceDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ReferenceDataSeeder.class);

    /** Paramètres minimaux (clé, valeur) — documentation de chaque clé en ligne. */
    static final List<String[]> MINIMAL_CONFIG = List.of(
            // Taille max d'une image (Mo) — miroir de app.storage.max-image-mb.
            new String[]{"storage.max-image-mb", "5"},
            // Taille de page par défaut des feeds — miroir de la pagination.
            new String[]{"feed.default-page-size", "10"},
            // Rétention des notifications lues (jours) avant purge éventuelle.
            new String[]{"notifications.retention-days", "365"});

    /**
     * Packs de coins initiaux (étape 14 : nom, coins, prix, devise, tri,
     * description). Prix EXEMPLES en XOF — ajustés hors code (console admin
     * future / migration), jamais dans React. Seedés UNE fois (par nom).
     */
    static final List<Object[]> INITIAL_PACKS = List.of(
            new Object[]{"Pack Starter", 500L, "500.0000", "XOF", 1,
                    "Pour découvrir les cadeaux en live"},
            new Object[]{"Pack Standard", 1000L, "1000.0000", "XOF", 2,
                    "Le plus choisi pour soutenir ses créateurs"},
            new Object[]{"Pack Premium", 5000L, "5000.0000", "XOF", 3,
                    "Pour les grands supporters"});

    private final CategoryRepository categories;
    private final AppConfigRepository configs;
    private final CoinPackageRepository packs;

    public ReferenceDataSeeder(CategoryRepository categories, AppConfigRepository configs,
                               CoinPackageRepository packs) {
        this.categories = categories;
        this.configs = configs;
        this.packs = packs;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed();
    }

    /** Idempotent : rejouable à chaque boot sans créer de doublon. */
    @Transactional
    public void seed() {
        int categoriesCreees = 0;
        for (CategoryCatalog.Entry entry : CategoryCatalog.INITIAL) {
            // Slug STABLE du catalogue (ex. "+243" → "plus243", que slugify("…")
            // ne reconstruirait pas) — jamais recalculé depuis le nom.
            if (!categories.existsBySlug(entry.slug())) {
                Category category = Category.create(entry.name(), entry.slug(), entry.description());
                category.updateDisplay(null, entry.displayOrder());
                categories.save(category);
                categoriesCreees++;
            }
        }
        int configsCreees = 0;
        for (String[] config : MINIMAL_CONFIG) {
            if (configs.findByConfigKeyOrderByVersionAsc(config[0]).isEmpty()) {
                configs.save(AppConfig.create(config[0], config[1]));
                configsCreees++;
            }
        }
        int packsCrees = 0;
        for (Object[] pack : INITIAL_PACKS) {
            String name = (String) pack[0];
            if (packs.findByName(name).isEmpty()) {
                packs.save(CoinPackage.create(name, (long) pack[1],
                        new java.math.BigDecimal((String) pack[2]), (String) pack[3],
                        (int) pack[4], (String) pack[5]));
                packsCrees++;
            }
        }
        log.info("Seed référence : {} catégories + {} paramètres + {} packs "
                        + "(idempotent, rejouable)",
                categoriesCreees, configsCreees, packsCrees);
    }
}
