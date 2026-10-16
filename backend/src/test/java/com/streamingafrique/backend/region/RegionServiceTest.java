package com.streamingafrique.backend.region;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.exception.ApiException;
import com.streamingafrique.backend.region.dto.CountryDto;
import com.streamingafrique.backend.region.dto.RegionConfigDto;
import com.streamingafrique.backend.region.model.Country;
import com.streamingafrique.backend.region.model.CountryPaymentMethod;
import com.streamingafrique.backend.region.repository.CountryPaymentMethodRepository;
import com.streamingafrique.backend.region.repository.CountryRepository;
import com.streamingafrique.backend.region.service.ExchangeRateService;
import com.streamingafrique.backend.region.service.RegionService;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.user.model.User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Étape 19 — référentiels Africa+ : pays seedés (pas hard-codés),
 * devises/langues dérivées des données, moyens préparés inactifs,
 * taux de change honnêtement indisponibles, garde ADMIN.
 */
@SpringBootTest
@Transactional
class RegionServiceTest {

    @Autowired
    private RegionService regions;
    @Autowired
    private ExchangeRateService exchange;
    @Autowired
    private CountryRepository countryRepository;
    @Autowired
    private CountryPaymentMethodRepository methodRepository;

    /** Jeux de test (le seed Flyway V23 ne tourne pas en tests H2). */
    @org.junit.jupiter.api.BeforeEach
    void seed() {
        if (countryRepository.count() == 0) {
            countryRepository.save(Country.create("CM", "Cameroun",
                    "CENTRAL", "237", "XAF", "fr-CM", "fr,en",
                    "Africa/Douala"));
            countryRepository.save(Country.create("CI", "Côte d'Ivoire",
                    "WEST", "225", "XOF", "fr-CI", "fr", "Africa/Abidjan"));
            countryRepository.save(Country.create("SN", "Sénégal",
                    "WEST", "221", "XOF", "fr-SN", "fr,wo", "Africa/Dakar"));
            countryRepository.save(Country.create("NG", "Nigeria",
                    "WEST", "234", "NGN", "en-NG", "en,ha,yo,ig",
                    "Africa/Lagos"));
            countryRepository.save(Country.create("KE", "Kenya",
                    "EAST", "254", "KES", "en-KE", "en,sw",
                    "Africa/Nairobi"));
            countryRepository.save(Country.create("ZA", "Afrique du Sud",
                    "SOUTHERN", "27", "ZAR", "en-ZA", "en",
                    "Africa/Johannesburg"));
            countryRepository.save(Country.create("GH", "Ghana",
                    "WEST", "233", "GHS", "en-GH", "en", "Africa/Accra"));
            methodRepository.save(CountryPaymentMethod.prepare("CM",
                    "MTN_MOMO", "MOBILE_MONEY", "XAF", 1));
            methodRepository.save(CountryPaymentMethod.prepare("CM",
                    "ORANGE_MONEY", "MOBILE_MONEY", "XAF", 2));
        }
    }

    private CurrentUser admin() {
        return CurrentUser.of(990001L, "r19-admin@example.com", User.Role.ADMIN);
    }

    private CurrentUser user() {
        return CurrentUser.of(990002L, "r19-user@example.com", User.Role.USER);
    }

    @Test
    void africanMarketsComeFromData() {
        List<CountryDto> countries = regions.activeCountries();
        assertEquals(7, countries.size());
        assertTrue(countries.stream().anyMatch(c -> c.code().equals("CM")));
        assertTrue(countries.stream().anyMatch(c -> c.code().equals("NG")));
        assertTrue(countries.stream().anyMatch(c -> c.code().equals("KE")));
    }

    @Test
    void cameroonIsXafButNotUniversal() {
        assertEquals("XAF", regions.country("CM").defaultCurrency());
        assertEquals("XOF", regions.country("CI").defaultCurrency());
        assertEquals("NGN", regions.country("NG").defaultCurrency());
        assertEquals("KES", regions.country("KE").defaultCurrency());
        assertEquals("ZAR", regions.country("ZA").defaultCurrency());
        assertEquals("XOF", regions.country("SN").defaultCurrency());
    }

    @Test
    void currenciesAndLocalesComeFromData() {
        List<String> currencies = regions.currencies();
        assertTrue(currencies.containsAll(
                List.of("XAF", "XOF", "NGN", "GHS", "KES", "ZAR")));
        List<String> locales = regions.locales();
        assertTrue(locales.containsAll(List.of("fr", "en", "wo", "sw")));
    }

    @Test
    void unknownCountryIs404() {
        assertThrows(ApiException.class, () -> regions.country("XX"));
        assertThrows(ApiException.class, () -> regions.country("C"));
    }

    @Test
    void paymentMethodsArePreparedButInactive() {
        var methods = regions.allPaymentMethods("CM", true);
        assertFalse(methods.isEmpty());
        // Aucun moyen réel activé sans intégration officielle.
        assertTrue(methods.stream().noneMatch(m -> m.active()));
        assertTrue(methods.stream().anyMatch(m -> m.provider().equals("MTN_MOMO")));
        // Vitrine publique : rien d'actif → liste vide, pas d'erreur.
        assertTrue(regions.paymentMethods("CM", true).isEmpty());
    }

    @Test
    void onlyAdminCanToggleCountry() {
        assertThrows(ApiException.class,
                () -> regions.setCountryActive("CM", false, user()));
        CountryDto updated = regions.setCountryActive("CM", false, admin());
        assertFalse(updated.active());
        assertEquals(6, regions.activeCountries().size());
        regions.setCountryActive("CM", true, admin());
    }

    @Test
    void exchangeRatesAreHonestlyUnavailable() {
        assertFalse(exchange.available());
        assertThrows(ApiException.class, () -> exchange.convert(
                new BigDecimal("1000"), "XAF", "EUR"));
        // Identique → identité (aucun taux requis).
        var same = exchange.convert(new BigDecimal("1000"), "XAF", "XAF");
        assertEquals(new BigDecimal("1000"), same.amount());
    }

    @Test
    void regionConfigExposesFlagsAndRateStatus() {
        RegionConfigDto config = regions.regionConfig();
        assertFalse(config.exchangeRatesAvailable());
        assertTrue(config.features().containsKey("features.africa_discovery"));
        assertTrue(config.features().containsKey("features.mobile_money"));
        assertEquals(7, config.activeCountries().size());
    }
}
