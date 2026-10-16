package com.streamingafrique.backend.region.dto;

import java.util.Arrays;
import java.util.List;

import com.streamingafrique.backend.region.model.Country;

/** Fiche pays publique (aucune donnée sensible). */
public record CountryDto(
        String code,
        String name,
        String region,
        String phoneCode,
        String defaultCurrency,
        String defaultLocale,
        List<String> supportedLanguages,
        String timezone,
        boolean active) {

    public static CountryDto from(Country country) {
        List<String> languages = country.getSupportedLanguages() == null
                ? List.of()
                : Arrays.stream(country.getSupportedLanguages().split(","))
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .toList();
        return new CountryDto(country.getCode(), country.getName(),
                country.getRegion(), country.getPhoneCode(),
                country.getDefaultCurrency(), country.getDefaultLocale(),
                languages, country.getTimezone(), country.isActive());
    }
}
