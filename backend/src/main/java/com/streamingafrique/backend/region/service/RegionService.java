package com.streamingafrique.backend.region.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamingafrique.backend.admin.repository.AppConfigRepository;
import com.streamingafrique.backend.exception.ApiException;
import com.streamingafrique.backend.redis.RedisCacheService;
import com.streamingafrique.backend.region.dto.CountryDto;
import com.streamingafrique.backend.region.dto.CountryPaymentMethodDto;
import com.streamingafrique.backend.region.dto.RegionConfigDto;
import com.streamingafrique.backend.region.model.Country;
import com.streamingafrique.backend.region.model.CountryPaymentMethod;
import com.streamingafrique.backend.region.repository.CountryPaymentMethodRepository;
import com.streamingafrique.backend.region.repository.CountryRepository;
import com.streamingafrique.backend.security.CurrentUser;

/**
 * Couche Africa+ (étape 19) — configuration régionale CACHÉE mais
 * toujours lue depuis PostgreSQL (source de vérité).
 *
 * Feature flags : réutilise {@code app_config} (clés
 * {@code features.*}, défaut = activé) — aucun second système.
 */
@Service
public class RegionService {

    private static final Logger log = LoggerFactory.getLogger(RegionService.class);

    /** Flags connus (défaut true si absents). */
    public static final List<String> KNOWN_FEATURES = List.of(
            "features.africa_discovery",
            "features.regional_trends",
            "features.mobile_money",
            "features.multi_currency",
            "features.data_saver",
            "features.multilingual_moderation");

    private final CountryRepository countries;
    private final CountryPaymentMethodRepository methods;
    private final AppConfigRepository configs;
    private final ExchangeRateService exchange;
    private final RedisCacheService cache;
    private final ObjectMapper mapper = new ObjectMapper();

    public RegionService(CountryRepository countries,
                         CountryPaymentMethodRepository methods,
                         AppConfigRepository configs,
                         ExchangeRateService exchange,
                         RedisCacheService cache) {
        this.countries = countries;
        this.methods = methods;
        this.configs = configs;
        this.exchange = exchange;
        this.cache = cache;
    }

    /* ─── Lecture publique (cachée) ─────────────────────────────── */

    @Transactional(readOnly = true)
    public List<CountryDto> activeCountries() {
        try {
            String cached = cache.get("region:countries");
            if (cached != null) {
                return mapper.readValue(cached,
                        new TypeReference<List<CountryDto>>() {
                        });
            }
        } catch (Exception ex) {
            log.debug("Cache pays illisible : {}", ex.getMessage());
        }
        List<CountryDto> dtos = countries.findByActiveTrueOrderByNameAsc()
                .stream().map(CountryDto::from).toList();
        try {
            cache.put("region:countries", mapper.writeValueAsString(dtos),
                    Duration.ofMinutes(15));
        } catch (Exception ex) {
            log.debug("Cache pays inscriptible : {}", ex.getMessage());
        }
        return dtos;
    }

    @Transactional(readOnly = true)
    public CountryDto country(String code) {
        String norm = normCode(code);
        return countries.findById(norm)
                .map(CountryDto::from)
                .orElseThrow(() -> ApiException.notFound("Pays inconnu : " + norm));
    }

    /** Langues supportées (union des pays actifs : "fr", "en", "wo"…​). */
    @Transactional(readOnly = true)
    public List<String> locales() {
        return activeCountries().stream()
                .flatMap(c -> c.supportedLanguages().stream())
                .distinct().sorted().toList();
    }

    /** Devises des pays actifs (XAF, XOF, NGN…​ — jamais inventées). */
    @Transactional(readOnly = true)
    public List<String> currencies() {
        return activeCountries().stream()
                .map(CountryDto::defaultCurrency)
                .distinct().sorted().toList();
    }

    /** Moyens de paiement d'un pays (public : ACTIFS + sandbox visibles). */
    @Transactional(readOnly = true)
    public List<CountryPaymentMethodDto> paymentMethods(String code,
                                                        boolean sandboxMode) {
        String norm = normCode(code);
        countries.findById(norm)
                .orElseThrow(() -> ApiException.notFound("Pays inconnu : " + norm));
        return methods.findByCountryCodeAndActiveTrueOrderBySortOrderAsc(norm)
                .stream()
                .map(r -> CountryPaymentMethodDto.from(r, sandboxMode))
                .toList();
    }

    /** Moyens (admin : tous, actifs ou préparés). */
    @Transactional(readOnly = true)
    public List<CountryPaymentMethodDto> allPaymentMethods(String code,
                                                           boolean sandboxMode) {
        String norm = normCode(code);
        return methods.findByCountryCodeOrderBySortOrderAsc(norm).stream()
                .map(r -> CountryPaymentMethodDto.from(r, sandboxMode))
                .toList();
    }

    /** Configuration publique : flags + taux + pays actifs. */
    @Transactional(readOnly = true)
    public RegionConfigDto regionConfig() {
        Map<String, Boolean> features = new LinkedHashMap<>();
        for (String flag : KNOWN_FEATURES) {
            features.put(flag, featureEnabled(flag));
        }
        List<String> active = activeCountries().stream()
                .map(CountryDto::code).toList();
        return new RegionConfigDto(features, exchange.available(), active);
    }

    public boolean featureEnabled(String flag) {
        try {
            var history = configs.findByConfigKeyOrderByVersionAsc(flag);
            if (history.isEmpty()) {
                return true;
            }
            return !"false".equalsIgnoreCase(
                    history.get(history.size() - 1).getValue().trim());
        } catch (Exception ex) {
            log.warn("Lecture flag {} impossible : {} — activé par défaut",
                    flag, ex.getMessage());
            return true;
        }
    }

    /* ─── Administration (ADMIN, auditée par l'appelant) ────────── */

    @Transactional
    public CountryDto setCountryActive(String code, boolean active,
                                       CurrentUser actor) {
        CurrentUser.requireAuthenticated(actor);
        if (!actor.isAdmin()) {
            throw ApiException.forbidden("Réservé à l'administration");
        }
        Country country = countries.findById(normCode(code))
                .orElseThrow(() -> ApiException.notFound("Pays inconnu : " + code));
        country.setActive(active);
        evict();
        return CountryDto.from(countries.save(country));
    }

    @Transactional
    public CountryPaymentMethodDto setMethodActive(Long id, boolean active,
                                                   CurrentUser actor) {
        CurrentUser.requireAuthenticated(actor);
        if (!actor.isAdmin()) {
            throw ApiException.forbidden("Réservé à l'administration");
        }
        CountryPaymentMethod row = methods.findById(id)
                .orElseThrow(() -> ApiException.notFound("Moyen inconnu : " + id));
        row.setActive(active);
        return CountryPaymentMethodDto.from(methods.save(row), false);
    }

    @Transactional
    public CountryPaymentMethodDto addMethod(String code, String provider,
                                             String method, String currency,
                                             CurrentUser actor) {
        CurrentUser.requireAuthenticated(actor);
        if (!actor.isAdmin()) {
            throw ApiException.forbidden("Réservé à l'administration");
        }
        String norm = normCode(code);
        countries.findById(norm)
                .orElseThrow(() -> ApiException.notFound("Pays inconnu : " + norm));
        if (currency == null || !currency.trim().matches("[A-Za-z]{3}")) {
            throw ApiException.badRequest("Devise invalide (ISO-3 attendue)");
        }
        List<String> knownProviders = new ArrayList<>(List.of("WAVE",
                "ORANGE_MONEY", "MTN_MOMO", "MPESA", "STRIPE", "PAYSTACK",
                "FLUTTERWAVE", "OTHER"));
        if (!knownProviders.contains(provider.trim().toUpperCase(Locale.ROOT))) {
            throw ApiException.badRequest("Prestataire inconnu : " + provider);
        }
        List<String> knownMethods = List.of("MOBILE_MONEY", "CARD",
                "BANK_TRANSFER", "OTHER");
        if (!knownMethods.contains(method.trim().toUpperCase(Locale.ROOT))) {
            throw ApiException.badRequest("Moyen inconnu : " + method);
        }
        return CountryPaymentMethodDto.from(methods.save(
                CountryPaymentMethod.prepare(norm, provider, method,
                        currency, 99)), false);
    }

    private String normCode(String code) {
        if (code == null || !code.trim().matches("[A-Za-z]{2}")) {
            throw ApiException.badRequest("Code pays invalide (ISO alpha-2)");
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private void evict() {
        try {
            cache.evict("region:countries");
        } catch (Exception ex) {
            log.debug("Invalidation cache pays impossible : {}", ex.getMessage());
        }
    }
}
