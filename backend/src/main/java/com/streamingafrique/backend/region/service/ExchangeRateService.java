package com.streamingafrique.backend.region.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.streamingafrique.backend.exception.ApiException;
import com.streamingafrique.backend.redis.RedisCacheService;
import com.streamingafrique.backend.region.rates.ExchangeRateProvider;

/**
 * Conversions multi-devises (étape 19) — BigDecimal + taux datés.
 *
 * Sans source fiable : {@link #available()} = false et toute conversion
 * est refusée en 503 explicite (jamais de taux approximatif ou inventé).
 * Avec source : taux mis en cache Redis (24 h) avec leur date.
 */
@Service
public class ExchangeRateService {

    private final List<ExchangeRateProvider> providers;
    private final RedisCacheService cache;

    public ExchangeRateService(List<ExchangeRateProvider> providers,
                               RedisCacheService cache) {
        this.providers = providers;
        this.cache = cache;
    }

    /** Une source réelle (non-noop) est-elle branchée ? */
    public boolean available() {
        return providers.stream()
                .anyMatch(p -> !"noop".equalsIgnoreCase(p.name()));
    }

    /**
     * Convertit {@code amount} de {@code from} vers {@code to}.
     * Identique → montant inchangé. Sinon 503 si aucune source.
     */
    public Conversion convert(BigDecimal amount, String from, String to) {
        if (amount == null || amount.signum() < 0) {
            throw ApiException.badRequest("Montant invalide");
        }
        String source = norm(from);
        String target = norm(to);
        if (source.equals(target)) {
            return new Conversion(amount, source, target, BigDecimal.ONE,
                    java.time.Instant.now(), "identity");
        }
        Optional<RateHit> hit = cachedOrFresh(source, target);
        RateHit rate = hit.orElseThrow(() -> new ApiException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "RATE_UNAVAILABLE",
                "Conversion " + source + " → " + target
                        + " indisponible : aucune source de taux fiable branchée"));
        BigDecimal converted = amount.multiply(rate.value())
                .setScale(2, RoundingMode.HALF_UP);
        return new Conversion(converted, source, target, rate.value(),
                rate.asOf(), rate.source());
    }

    private Optional<RateHit> cachedOrFresh(String from, String to) {
        String key = "fx:" + from + ":" + to;
        try {
            String cached = cache.get(key);
            if (cached != null) {
                String[] parts = cached.split("\\|");
                if (parts.length == 3) {
                    return Optional.of(new RateHit(new BigDecimal(parts[0]),
                            java.time.Instant.parse(parts[1]), parts[2]));
                }
            }
        } catch (Exception ignored) {
            // Cache illisible → source fraîche.
        }
        for (ExchangeRateProvider provider : providers) {
            Optional<ExchangeRateProvider.Rate> rate =
                    provider.rate(from, to);
            if (rate.isPresent()) {
                RateHit hit = new RateHit(rate.get().value(),
                        rate.get().asOf(), provider.name());
                try {
                    cache.put(key, hit.value().toPlainString() + "|"
                            + hit.asOf().toString() + "|" + hit.source(),
                            Duration.ofHours(24));
                } catch (Exception ignored) {
                    // Mise en cache best-effort.
                }
                return Optional.of(hit);
            }
        }
        return Optional.empty();
    }

    private String norm(String currency) {
        if (currency == null || !currency.trim().matches("[A-Za-z]{3}")) {
            throw ApiException.badRequest("Devise invalide (ISO-3 attendue)");
        }
        return currency.trim().toUpperCase(java.util.Locale.ROOT);
    }

    public record Conversion(BigDecimal amount, String from, String to,
                             BigDecimal rate, java.time.Instant asOf,
                             String source) {
    }

    private record RateHit(BigDecimal value, java.time.Instant asOf,
                           String source) {
    }
}
