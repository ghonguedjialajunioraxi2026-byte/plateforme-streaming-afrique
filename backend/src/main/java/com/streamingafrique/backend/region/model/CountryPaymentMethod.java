package com.streamingafrique.backend.region.model;

import com.streamingafrique.backend.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Moyen de paiement d'un pays (étape 19) → {@code country_payment_methods}.
 *
 * {@code active=false} = PRÉPARÉ mais non intégré (aucune API inventée,
 * aucun webhook simulé) : l'activation exige une intégration officielle
 * câblée + identifiants réels. Le {@code provider} reprend l'enum
 * {@code Payment.Provider} (WAVE, ORANGE_MONEY, MTN_MOMO, MPESA…​).
 */
@Entity
@Table(name = "country_payment_methods",
        uniqueConstraints = @UniqueConstraint(name = "uk_country_provider_method",
                columnNames = {"countryCode", "provider", "method"}),
        indexes = @Index(name = "idx_cpm_country_active",
                columnList = "countryCode,active,sortOrder"))
public class CountryPaymentMethod extends BaseEntity {

    @Column(nullable = false, length = 2)
    private String countryCode;

    @Column(nullable = false, length = 30)
    private String provider;

    /** MOBILE_MONEY, CARD, BANK_TRANSFER, OTHER (cf. Payment.Method). */
    @Column(nullable = false, length = 30)
    private String method;

    /** Devise ISO-3 de ce moyen dans ce pays. */
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private boolean active = false;

    @Column(nullable = false)
    private int sortOrder = 0;

    protected CountryPaymentMethod() {
    }

    public static CountryPaymentMethod prepare(String countryCode, String provider,
                                               String method, String currency,
                                               int sortOrder) {
        CountryPaymentMethod row = new CountryPaymentMethod();
        row.countryCode = countryCode.trim().toUpperCase(java.util.Locale.ROOT);
        row.provider = provider.trim().toUpperCase(java.util.Locale.ROOT);
        row.method = method.trim().toUpperCase(java.util.Locale.ROOT);
        row.currency = currency.trim().toUpperCase(java.util.Locale.ROOT);
        row.active = false;
        row.sortOrder = sortOrder;
        return row;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getProvider() {
        return provider;
    }

    public String getMethod() {
        return method;
    }

    public String getCurrency() {
        return currency;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
