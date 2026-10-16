package com.streamingafrique.backend.region.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.streamingafrique.backend.dto.ApiResponse;
import com.streamingafrique.backend.payment.config.PaymentProperties;
import com.streamingafrique.backend.region.dto.CountryDto;
import com.streamingafrique.backend.region.dto.CountryPaymentMethodDto;
import com.streamingafrique.backend.region.dto.RegionConfigDto;
import com.streamingafrique.backend.region.service.RegionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Référentiels Africa+ PUBLICS (étape 19) — lus depuis PostgreSQL
 * (jamais hard-codés), cachés 15 min. Aucune donnée sensible.
 */
@Tag(name = "Région Africa+",
        description = "Pays, langues, devises, moyens de paiement par pays, "
                + "configuration régionale. Lecture publique.")
@RestController
@RequestMapping("/api")
public class RegionController {

    private final RegionService regions;
    private final PaymentProperties payments;

    public RegionController(RegionService regions,
                            PaymentProperties payments) {
        this.regions = regions;
        this.payments = payments;
    }

    @Operation(summary = "Pays actifs",
            description = "Marchés configurés (code ISO, devise, locale, "
                    + "langues, fuseau). Le Cameroun n'est qu'une ligne.")
    @GetMapping("/countries")
    public ApiResponse<List<CountryDto>> countries() {
        return ApiResponse.success("Pays", regions.activeCountries());
    }

    @Operation(summary = "Détail d'un pays")
    @GetMapping("/countries/{code}")
    public ApiResponse<CountryDto> country(@PathVariable String code) {
        return ApiResponse.success("Pays", regions.country(code));
    }

    @Operation(summary = "Langues supportées",
            description = "Union des langues des pays actifs (fr, en, wo…​).")
    @GetMapping("/locales")
    public ApiResponse<List<String>> locales() {
        return ApiResponse.success("Langues", regions.locales());
    }

    @Operation(summary = "Devises des pays actifs",
            description = "XAF, XOF, NGN…​ — jamais inventées, jamais universelles.")
    @GetMapping("/currencies")
    public ApiResponse<List<String>> currencies() {
        return ApiResponse.success("Devises", regions.currencies());
    }

    @Operation(summary = "Moyens de paiement d'un pays",
            description = "Ex. `?country=CM`. Seuls les moyens ACTIFS sont "
                    + "listés ; `active=false` = préparé, non intégré.")
    @GetMapping("/payment-methods")
    public ApiResponse<List<CountryPaymentMethodDto>> paymentMethods(
            @RequestParam String country) {
        return ApiResponse.success("Moyens de paiement",
                regions.paymentMethods(country, payments.isSandboxEnabled()));
    }

    @Operation(summary = "Configuration régionale",
            description = "Feature flags `features.*`, disponibilité des taux "
                    + "de change (false = aucune source fiable), pays actifs.")
    @GetMapping("/config/region")
    public ApiResponse<RegionConfigDto> regionConfig() {
        return ApiResponse.success("Configuration régionale",
                regions.regionConfig());
    }
}
