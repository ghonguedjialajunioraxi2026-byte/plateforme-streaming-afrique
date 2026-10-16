package com.streamingafrique.backend.admin.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.admin.dto.ConfigDto;
import com.streamingafrique.backend.admin.model.AppConfig;
import com.streamingafrique.backend.admin.repository.AppConfigRepository;
import com.streamingafrique.backend.dto.ApiPageResponse;
import com.streamingafrique.backend.exception.ApiException;
import com.streamingafrique.backend.security.CurrentUser;

/**
 * Paramètres globaux versionnés (étape 17 : /admin/settings).
 *
 * Lecture : valeur courante = version max par clé. Écriture : NOUVELLE ligne
 * (version + 1, jamais d'UPDATE — historique complet, rollback trivial).
 * Chaque écriture est auditée. L'application des valeurs aux comportements
 * reste du ressort des étapes fonctionnelles (ici : socle administrable).
 */
@Service
public class AdminConfigService {

    private final AppConfigRepository configs;
    private final AdminAuditService audit;

    public AdminConfigService(AppConfigRepository configs, AdminAuditService audit) {
        this.configs = configs;
        this.audit = audit;
    }

    /** Valeurs courantes (une par clé), triées par clé. */
    @Transactional(readOnly = true)
    public ApiPageResponse<ConfigDto> current(CurrentUser actor, Pageable pageable) {
        CurrentUser.requireAuthenticated(actor);
        if (!actor.isAdmin()) {
            throw ApiException.forbidden("Administration réservée");
        }
        List<ConfigDto> all = configs.findAll().stream()
                .collect(java.util.stream.Collectors.groupingBy(AppConfig::getConfigKey,
                        java.util.stream.Collectors.maxBy(
                                Comparator.comparingInt(AppConfig::getVersion))))
                .values().stream()
                .flatMap(java.util.Optional::stream)
                .map(ConfigDto::from)
                .sorted(Comparator.comparing(ConfigDto::key))
                .toList();
        return ApiPageResponse.of(all, pageable.getPageNumber(), pageable.getPageSize(),
                all.size());
    }

    /** Nouvelle version d'un paramètre (ADMIN + audit). */
    @Transactional
    public ConfigDto set(String key, String value, CurrentUser actor) {
        CurrentUser.requireAuthenticated(actor);
        if (!actor.isAdmin()) {
            throw ApiException.forbidden("Administration réservée");
        }
        String cleanKey = key != null ? key.trim() : "";
        if (cleanKey.isEmpty() || value == null) {
            throw ApiException.badRequest("Clé et valeur sont obligatoires");
        }
        AppConfig saved = configs.findByConfigKeyOrderByVersionAsc(cleanKey).stream()
                .max(Comparator.comparingInt(AppConfig::getVersion))
                .map(latest -> configs.save(latest.nextVersion(value)))
                .orElseGet(() -> configs.save(AppConfig.create(cleanKey, value)));
        audit.record(actor, "CONFIG_CHANGED", "CONFIG", null,
                null, cleanKey + " = " + value + " (v" + saved.getVersion() + ")",
                "Réglage plateforme");
        return ConfigDto.from(saved);
    }
}
