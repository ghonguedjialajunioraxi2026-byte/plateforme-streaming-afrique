package com.streamingafrique.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Active l'audit JPA des entités : les annotations @CreatedDate et
 * @LastModifiedDate de {@link com.streamingafrique.backend.common.model.BaseEntity}
 * sont remplies automatiquement à chaque enregistrement/modification,
 * sans aucune ligne de code dans les services.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}