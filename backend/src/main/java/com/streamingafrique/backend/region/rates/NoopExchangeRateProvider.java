package com.streamingafrique.backend.region.rates;

import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * Source de taux VIDE (défaut) : aucune conversion n'est possible tant
 * qu'une source fiable n'est pas branchée. Préférable à des taux
 * inventés qui fausseraient la monnaie des utilisateurs.
 */
@Component
public class NoopExchangeRateProvider implements ExchangeRateProvider {

    @Override
    public String name() {
        return "noop";
    }

    @Override
    public Optional<Rate> rate(String fromCurrency, String toCurrency) {
        return Optional.empty();
    }
}
