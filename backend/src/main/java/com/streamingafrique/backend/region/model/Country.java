package com.streamingafrique.backend.region.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Pays configurable (étape 19) → table {@code countries}.
 *
 * AUCUN pays n'est hard-codé dans le code : les marchés (devise,
 * locale, langues, fuseau) viennent de cette table, modifiable par
 * l'admin. Le Cameroun n'est qu'une ligne parmi d'autres.
 */
@Entity
@Table(name = "countries")
public class Country {

    /** Code ISO alpha-2 (ex. "CM") — clé primaire stable. */
    @Id
    @Column(length = 2)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    /** CENTRAL, WEST, EAST, NORTH, SOUTHERN. */
    @Column(nullable = false, length = 20)
    private String region = "WEST";

    @Column(name = "phone_code", length = 8)
    private String phoneCode;

    /** Devise ISO-3 par défaut (ex. "XAF" — jamais universelle). */
    @Column(name = "default_currency", nullable = false, length = 3)
    private String defaultCurrency;

    /** Locale par défaut (ex. "fr-CM"). */
    @Column(name = "default_locale", nullable = false, length = 10)
    private String defaultLocale;

    /** Langues supportées, CSV (ex. "fr,en") — extensible sans migration. */
    @Column(name = "supported_languages", nullable = false, length = 100)
    private String supportedLanguages = "fr,en";

    /** Fuseau IANA (ex. "Africa/Douala"). */
    @Column(nullable = false, length = 60)
    private String timezone = "UTC";

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Country() {
    }

    /** Fabrique (seed, tests, futur endpoint admin de création). */
    public static Country create(String code, String name, String region,
                                 String phoneCode, String defaultCurrency,
                                 String defaultLocale, String supportedLanguages,
                                 String timezone) {
        if (code == null || !code.trim().matches("[A-Za-z]{2}")) {
            throw new IllegalArgumentException("Code pays ISO alpha-2 requis");
        }
        if (defaultCurrency == null
                || !defaultCurrency.trim().matches("[A-Za-z]{3}")) {
            throw new IllegalArgumentException("Devise ISO-3 requise");
        }
        Country country = new Country();
        country.code = code.trim().toUpperCase(java.util.Locale.ROOT);
        country.name = name;
        country.region = region;
        country.phoneCode = phoneCode;
        country.defaultCurrency = defaultCurrency.trim()
                .toUpperCase(java.util.Locale.ROOT);
        country.defaultLocale = defaultLocale;
        country.supportedLanguages = supportedLanguages;
        country.timezone = timezone;
        country.active = true;
        return country;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getRegion() {
        return region;
    }

    public String getPhoneCode() {
        return phoneCode;
    }

    public String getDefaultCurrency() {
        return defaultCurrency;
    }

    public String getDefaultLocale() {
        return defaultLocale;
    }

    public String getSupportedLanguages() {
        return supportedLanguages;
    }

    public String getTimezone() {
        return timezone;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
        this.updatedAt = Instant.now();
    }
}
