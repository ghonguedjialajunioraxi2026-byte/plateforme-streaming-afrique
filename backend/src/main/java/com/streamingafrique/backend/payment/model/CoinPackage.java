package com.streamingafrique.backend.payment.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import org.hibernate.annotations.Check;

import com.streamingafrique.backend.common.model.BaseEntity;

/**
 * ÉTAPE 14 — Catalogue des packs de coins achetables en monnaie fiat.
 *
 * SOURCE DE VÉRITÉ SERVEUR (§14-15) : le frontend n'envoie que
 * {@code packageId} ; prix, devise et quantité de coins sont relus ici.
 * Prix codés en base, jamais dans React.
 */
@Entity
@Table(name = "coin_packages", indexes = {
        @Index(name = "idx_coin_packages_active_order", columnList = "active,sortOrder")
})
@Check(constraints = "coins_amount > 0 AND price_amount > 0")
public class CoinPackage extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private long coinsAmount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal priceAmount;

    @Column(nullable = false, length = 3)
    private String priceCurrency;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private int sortOrder = 0;

    @Column(length = 500)
    private String description;

    protected CoinPackage() {
    }

    public static CoinPackage create(String name, long coinsAmount, BigDecimal priceAmount,
                                     String priceCurrency, int sortOrder, String description) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Le nom du pack est obligatoire");
        }
        if (coinsAmount <= 0 || priceAmount == null || priceAmount.signum() <= 0) {
            throw new IllegalArgumentException("Coins et prix strictement positifs exigés");
        }
        if (priceCurrency == null || !priceCurrency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Devise ISO à 3 lettres exigée");
        }
        CoinPackage pack = new CoinPackage();
        pack.name = name.trim();
        pack.coinsAmount = coinsAmount;
        pack.priceAmount = priceAmount;
        pack.priceCurrency = priceCurrency;
        pack.active = true;
        pack.sortOrder = sortOrder;
        pack.description = description;
        return pack;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public String getName() {
        return name;
    }

    public long getCoinsAmount() {
        return coinsAmount;
    }

    public BigDecimal getPriceAmount() {
        return priceAmount;
    }

    public String getPriceCurrency() {
        return priceCurrency;
    }

    public boolean isActive() {
        return active;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public String getDescription() {
        return description;
    }
}
