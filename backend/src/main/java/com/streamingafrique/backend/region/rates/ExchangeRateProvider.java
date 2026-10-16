package com.streamingafrique.backend.region.rates;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Abstraction des taux de change (étape 19) — JAMAIS de taux inventé.
 *
 * Brancher une source fiable = implémenter cette interface (date du
 * taux incluse, cache géré par le service). Tant qu'aucune source
 * n'est câblée, {@code NoopExchangeRateProvider} répond vide et les
 * conversions sont refusées explicitement (jamais approximées).
 */
public interface ExchangeRateProvider {

    /** Nom de la source (ex. "noop", "tcmb"…​). */
    String name();

    /**
     * Taux {@code from → to} + sa date, ou vide si indisponible.
     * Montants en {@link BigDecimal} — jamais float/double.
     */
    Optional<Rate> rate(String fromCurrency, String toCurrency);

    /** Taux daté (1 unité {@code from} = {@code value} unités {@code to}). */
    record Rate(BigDecimal value, java.time.Instant asOf) {
    }
}
