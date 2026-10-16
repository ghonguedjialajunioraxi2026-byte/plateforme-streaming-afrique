package com.streamingafrique.backend.region.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.streamingafrique.backend.dto.ApiResponse;
import com.streamingafrique.backend.region.dto.CountryDto;
import com.streamingafrique.backend.region.dto.CountryPaymentMethodDto;
import com.streamingafrique.backend.region.service.RegionService;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.security.EndpointTier;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Administration Africa+ (étape 19) — ADMIN uniquement, vérifié backend.
 * Les flags `features.*` passent par `PUT /api/admin/config` existant.
 */
@Tag(name = "Région Africa+ (admin)",
        description = "Activation pays / moyens de paiement. ADMIN uniquement.")
@SecurityRequirements({@SecurityRequirement(name = "bearerAuth"),
        @SecurityRequirement(name = "basicAuth")})
@PreAuthorize(EndpointTier.ADMIN)
@RestController
@RequestMapping("/api/admin/region")
public class RegionAdminController {

    private final RegionService regions;

    public RegionAdminController(RegionService regions) {
        this.regions = regions;
    }

    public record ActiveRequest(boolean active) {
    }

    public record PaymentMethodRequest(
            @NotBlank String provider,
            @NotBlank String method,
            @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency) {
    }

    @Operation(summary = "Activer / désactiver un pays")
    @PutMapping("/countries/{code}")
    public ApiResponse<CountryDto> setCountryActive(
            @PathVariable String code,
            @Valid @RequestBody ActiveRequest request,
            Authentication authentication) {
        return ApiResponse.success("Pays mis à jour", regions.setCountryActive(
                code, request.active(), CurrentUser.from(authentication)));
    }

    @Operation(summary = "Tous les moyens d'un pays (actifs + préparés)")
    @GetMapping("/countries/{code}/payment-methods")
    public ApiResponse<List<CountryPaymentMethodDto>> allMethods(
            @PathVariable String code) {
        return ApiResponse.success("Moyens",
                regions.allPaymentMethods(code, false));
    }

    @Operation(summary = "Préparer un moyen de paiement (inactif par défaut)",
            description = "L'activation exige une intégration officielle câblée.")
    @PostMapping("/countries/{code}/payment-methods")
    public ApiResponse<CountryPaymentMethodDto> addMethod(
            @PathVariable String code,
            @Valid @RequestBody PaymentMethodRequest request,
            Authentication authentication) {
        return ApiResponse.success("Moyen préparé", regions.addMethod(code,
                request.provider(), request.method(), request.currency(),
                CurrentUser.from(authentication)));
    }

    @Operation(summary = "Activer / désactiver un moyen de paiement")
    @PutMapping("/payment-methods/{id}")
    public ApiResponse<CountryPaymentMethodDto> setMethodActive(
            @PathVariable Long id,
            @Valid @RequestBody ActiveRequest request,
            Authentication authentication) {
        return ApiResponse.success("Moyen mis à jour", regions.setMethodActive(
                id, request.active(), CurrentUser.from(authentication)));
    }
}
