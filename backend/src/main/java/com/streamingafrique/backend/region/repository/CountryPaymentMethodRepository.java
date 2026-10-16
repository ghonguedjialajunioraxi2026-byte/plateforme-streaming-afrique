package com.streamingafrique.backend.region.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.streamingafrique.backend.region.model.CountryPaymentMethod;

@Repository
public interface CountryPaymentMethodRepository
        extends JpaRepository<CountryPaymentMethod, Long> {

    /** Moyens d'un pays, ordre d'affichage (admin : tout). */
    List<CountryPaymentMethod> findByCountryCodeOrderBySortOrderAsc(String countryCode);

    /** Moyens ACTIFS d'un pays (vitrine publique). */
    List<CountryPaymentMethod> findByCountryCodeAndActiveTrueOrderBySortOrderAsc(
            String countryCode);
}
