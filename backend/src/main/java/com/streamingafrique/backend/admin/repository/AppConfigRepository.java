package com.streamingafrique.backend.admin.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.streamingafrique.backend.admin.model.AppConfig;

/**
 * Accès aux paramètres {app_config}.
 * Historique par clé + version précise. AUCUNE logique métier ici.
 */
public interface AppConfigRepository extends JpaRepository<AppConfig, Long> {

    /** Historique d'un paramètre (versions croissantes). */
    List<AppConfig> findByConfigKeyOrderByVersionAsc(String configKey);

    /** Une version précise (rollback, audit). */
    Optional<AppConfig> findByConfigKeyAndVersion(String configKey, int version);
}
