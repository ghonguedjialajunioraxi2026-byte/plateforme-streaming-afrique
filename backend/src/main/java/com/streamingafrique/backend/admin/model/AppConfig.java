package com.streamingafrique.backend.admin.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.streamingafrique.backend.common.model.BaseEntity;

/**
 * Entité "AppConfig" → table {@code app_config}.
 *
 * RÔLE : les paramètres globaux de la plateforme (quotas, seuils, feature
 * flags, taux affichés…). Versionnée : chaque changement = NOUVELLE ligne
 * (même clé, version + 1, date d'effet) — jamais d'UPDATE destructeur, donc
 * historique complet et rollback trivial (réactiver l'ancienne version).
 * La valeur courante d'une clé = version max (requête service, étape ultérieure).
 */
@Entity
@Table(name = "app_config",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_config_key_version", columnNames = {"configKey", "version"})
        },
        indexes = {
                @Index(name = "idx_config_key", columnList = "configKey")
        })
public class AppConfig extends BaseEntity {

    /** Clé du paramètre (ex. "upload.max-image-mb", "features.gifts"). */
    @Column(nullable = false, length = 100)
    private String configKey;

    /**
     * Valeur (texte : nombre, booléen, JSON simple selon la clé).
     * Colonne physique {@code config_value} : {@code value} est un mot
     * réservé SQL (H2/PostgreSQL).
     */
    @Column(name = "config_value", nullable = false, length = 2000)
    private String value;

    /** Version (1, 2, …) — croissante par clé. */
    @Column(nullable = false)
    private int version = 1;

    /** Date d'effet de cette version. */
    @Column(nullable = false)
    private Instant effectiveFrom = Instant.now();

    /** Constructeur vide requis par JPA (ne pas utiliser autrement). */
    protected AppConfig() {
    }

    /** Fabrique : première version d'un paramètre. */
    public static AppConfig create(String configKey, String value) {
        if (configKey == null || configKey.trim().isEmpty() || value == null) {
            throw new IllegalArgumentException("Clé et valeur sont obligatoires");
        }
        AppConfig config = new AppConfig();
        config.configKey = configKey;
        config.value = value;
        config.version = 1;
        config.effectiveFrom = Instant.now();
        return config;
    }

    /** Nouvelle version (l'ancienne ligne reste — historique). */
    public AppConfig nextVersion(String value) {
        if (value == null) {
            throw new IllegalArgumentException("La valeur est obligatoire");
        }
        AppConfig next = new AppConfig();
        next.configKey = this.configKey;
        next.value = value;
        next.version = this.version + 1;
        next.effectiveFrom = Instant.now();
        return next;
    }

    public String getConfigKey() {
        return configKey;
    }

    public String getValue() {
        return value;
    }

    public int getVersion() {
        return version;
    }

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }
}
