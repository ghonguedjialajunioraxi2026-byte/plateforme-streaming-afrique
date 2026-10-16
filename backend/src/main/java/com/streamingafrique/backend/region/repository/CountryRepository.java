package com.streamingafrique.backend.region.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.streamingafrique.backend.region.model.Country;

@Repository
public interface CountryRepository extends JpaRepository<Country, String> {

    /** Pays actifs (vitrine publique), triés par nom. */
    List<Country> findByActiveTrueOrderByNameAsc();
}
