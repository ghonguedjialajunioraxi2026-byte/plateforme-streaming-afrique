package com.streamingafrique.backend.wallet.model;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import com.streamingafrique.backend.common.model.BaseEntity;

/**
 * Entité "Currency" → table {@code currencies}.
 *
 * RÔLE : le référentiel de la monnaie virtuelle interne (sous-étape 7.21).
 * Une seule monnaie interne (« coins ») : les taux d'achat (fiat → coins)
 * et de reversement (coins → fiat créateur) + frais plateforme y sont
 * versionnés (nouvelle ligne à chaque changement, jamais d'UPDATE).
 *
 * Précision (décision 7.21) : les TAUX sont en {@code NUMERIC(19,4)} —
 * seule la conversion fiat↔coins les utilise, avec arrondi explicite côté
 * service. Les SOLDES et MONTANTS en coins sont en BIGINT d'unités
 * minimales (arithmétique entière exacte, aucune erreur flottante).
 */
@Entity
@Table(name = "currencies", indexes = {
        @Index(name = "idx_currencies_code_effective", columnList = "code,effectiveFrom")
})
public class Currency extends BaseEntity {

    /** Code interne (ex. "COIN"). */
    @Column(nullable = false, length = 20)
    private String code;

    /** Nom (ex. "Coins FLICK"). */
    @Column(nullable = false, length = 100)
    private String name;

    /** Montant fiat pour 1000 coins à l'achat (ex. XOF). */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal buyRateFiatPer1000;

    /** Montant fiat reversé pour 1000 coins (créateur). */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal payoutRateFiatPer1000;

    /** Frais plateforme en points de base (100 = 1 %). */
    @Column(nullable = false)
    private int feeBps = 0;

    /** Date d'effet de cette version. */
    @Column(nullable = false)
    private Instant effectiveFrom = Instant.now();

    /** Version courante (une seule par code, gérée par le service). */
    @Column(nullable = false)
    private boolean current = true;

    /** Constructeur vide requis par JPA (ne pas utiliser autrement). */
    protected Currency() {
    }

    /** Fabrique : taux strictement positifs, frais 0..10000 bps. */
    public static Currency create(String code, String name, BigDecimal buyRateFiatPer1000,
                                  BigDecimal payoutRateFiatPer1000, int feeBps) {
        if (buyRateFiatPer1000 == null || buyRateFiatPer1000.signum() <= 0
                || payoutRateFiatPer1000 == null || payoutRateFiatPer1000.signum() <= 0) {
            throw new IllegalArgumentException("Les taux doivent être strictement positifs");
        }
        if (feeBps < 0 || feeBps > 10000) {
            throw new IllegalArgumentException("Les frais doivent être entre 0 et 10000 points de base");
        }
        Currency currency = new Currency();
        currency.code = code;
        currency.name = name;
        currency.buyRateFiatPer1000 = buyRateFiatPer1000;
        currency.payoutRateFiatPer1000 = payoutRateFiatPer1000;
        currency.feeBps = feeBps;
        currency.effectiveFrom = Instant.now();
        currency.current = true;
        return currency;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getBuyRateFiatPer1000() {
        return buyRateFiatPer1000;
    }

    public BigDecimal getPayoutRateFiatPer1000() {
        return payoutRateFiatPer1000;
    }

    public int getFeeBps() {
        return feeBps;
    }

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }

    public boolean isCurrent() {
        return current;
    }
}
