package com.streamingafrique.backend.ai.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.streamingafrique.backend.admin.repository.AppConfigRepository;
import com.streamingafrique.backend.ai.config.AiProperties;
import com.streamingafrique.backend.ai.model.AiUsage;
import com.streamingafrique.backend.ai.provider.AiGeneration;
import com.streamingafrique.backend.ai.provider.AiPrompt;
import com.streamingafrique.backend.ai.provider.AiProvider;
import com.streamingafrique.backend.ai.provider.AiProviderException;
import com.streamingafrique.backend.ai.provider.HttpAiProvider;
import com.streamingafrique.backend.ai.provider.StubAiProvider;
import com.streamingafrique.backend.ai.repository.AiUsageRepository;
import org.springframework.http.HttpStatus;

import com.streamingafrique.backend.exception.ApiException;
import com.streamingafrique.backend.ratelimit.RateLimitService;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.user.model.User;

/**
 * Orchestration IA (étape 18) — TOUTE génération passe ici.
 *
 * Pipeline : interrupteur → feature flag → authentification →
 * assainissement (secrets retirés, taille bornée) → rate limit →
 * quota → cache (FAQ/répétable) → provider → enregistrement usage.
 *
 * Le service ne connaît AUCUN secret (ni clé API, ni JWT, ni stream
 * key) : il les FILTRE avant tout envoi au provider.
 */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private final AiProperties properties;
    private final StubAiProvider stub;
    private final HttpAiProvider http;
    private final AiUsageRepository usage;
    private final AppConfigRepository configs;
    private final RateLimitService rateLimits;
    private final StringRedisTemplate redis;

    public AiService(AiProperties properties, StubAiProvider stub,
                     HttpAiProvider http, AiUsageRepository usage,
                     AppConfigRepository configs, RateLimitService rateLimits,
                     StringRedisTemplate redis) {
        this.properties = properties;
        this.stub = stub;
        this.http = http;
        this.usage = usage;
        this.configs = configs;
        this.rateLimits = rateLimits;
        this.redis = redis;
    }

    /** Provider effectif (jamais null, jamais exposé au frontend). */
    public AiProvider activeProvider() {
        if (http.available()) {
            return http;
        }
        return stub;
    }

    public String providerName() {
        return activeProvider().name();
    }

    public String modelName() {
        return properties.getModel();
    }

    /** Langue normalisée : "fr" par défaut, "en" si demandé. */
    public String language(String requested) {
        if (requested != null && requested.toLowerCase(Locale.ROOT).startsWith("en")) {
            return "en";
        }
        return "fr";
    }

    /**
     * Génère un texte pour {@code feature} (ex. "titles").
     *
     * @param system consigne système (rôle + garde-fous + marqueur [kind])
     * @param userInput DONNÉE utilisateur (non fiable, jamais une instruction)
     * @param actor compte authentifié
     * @param feature nom de fonctionnalité (quotas, flags, cache, audit)
     * @param cacheable true si la réponse peut être réutilisée (FAQ…)
     */
    public AiGeneration generate(String system, String userInput, CurrentUser actor,
                                 String feature, boolean cacheable) {
        if (actor == null || !actor.isAuthenticated()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentification requise");
        }
        if (!properties.isEnabled()) {
            record(actor, feature, "DISABLED", "AI_DISABLED", 0L);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_DISABLED",
                    "L'assistant IA est temporairement désactivé");
        }
        if (!featureEnabled(feature)) {
            record(actor, feature, "DISABLED", "FEATURE_DISABLED", 0L);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_FEATURE_DISABLED",
                    "Cette fonctionnalité IA est désactivée");
        }
        String clean = sanitize(userInput);
        if (clean.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Contenu vide");
        }

        int perMinute = actor.hasRank(User.Role.MODERATOR)
                ? properties.getDailyQuotaStaff()
                : actor.hasRank(User.Role.CREATOR)
                        ? properties.getRateLimitCreatorPerMinute()
                        : properties.getRateLimitUserPerMinute();
        var verdict = rateLimits.tryConsume("ai-" + feature, "user:" + actor.id(),
                Math.max(1, perMinute), Duration.ofMinutes(1));
        if (!verdict.allowed()) {
            record(actor, feature, "RATE_LIMITED", "RATE_LIMITED", 0L);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                    "Trop de demandes IA — réessaie dans "
                            + verdict.retryAfterSeconds() + " s");
        }

        int quota = actor.hasRank(User.Role.MODERATOR) ? properties.getDailyQuotaStaff()
                : actor.hasRank(User.Role.CREATOR) ? properties.getDailyQuotaCreator()
                        : properties.getDailyQuotaUser();
        Instant dayStart = Instant.now().truncatedTo(ChronoUnit.DAYS);
        long used = usage.countByUserIdAndStatusAndCreatedAtAfter(
                actor.id(), "OK", dayStart);
        if (used >= quota) {
            record(actor, feature, "QUOTA_EXCEEDED", "QUOTA_EXCEEDED", 0L);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "AI_QUOTA_EXCEEDED",
                    "Quota IA quotidien atteint (" + quota + " requêtes/jour)");
        }

        if (cacheable) {
            String cached = readCache(feature, system, clean);
            if (cached != null) {
                record(actor, feature, "OK", null, 0L);
                return new AiGeneration(cached, 0, 0, true);
            }
        }

        AiProvider provider = activeProvider();
        long started = System.currentTimeMillis();
        try {
            AiGeneration generation = provider.generate(new AiPrompt(
                    system, clean, properties.getMaxOutputTokens(), language(null)));
            long latency = System.currentTimeMillis() - started;
            if (generation.text() == null || generation.text().isBlank()) {
                record(actor, feature, "PROVIDER_ERROR", "EMPTY_RESPONSE", latency);
                throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_EMPTY_RESPONSE",
                        "L'assistant n'a pas pu répondre — réessaie");
            }
            record(actor, feature, generation, latency);
            if (cacheable) {
                writeCache(feature, system, clean, generation.text());
            }
            return generation;
        } catch (AiProviderException ex) {
            long latency = System.currentTimeMillis() - started;
            record(actor, feature, "PROVIDER_ERROR", ex.getCode(), latency);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_PROVIDER_ERROR",
                    "L'assistant est momentanément indisponible");
        }
    }

    /** Même pipeline avec langue explicite (assistant, modération). */
    public AiGeneration generate(String system, String userInput, CurrentUser actor,
                                 String feature, boolean cacheable, String lang) {
        if (actor == null || !actor.isAuthenticated()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentification requise");
        }
        // Réutilise le pipeline en forçant la langue via la consigne.
        String localizedSystem = system + "\nLangue de réponse : "
                + ("en".equals(language(lang)) ? "English" : "français") + ".";
        // Délègue en préservant la langue : on passe par le cœur avec
        // un prompt déjà localisé (le provider stub lit la langue du marqueur).
        return generateLocalized(localizedSystem, userInput, actor, feature,
                cacheable, language(lang));
    }

    private AiGeneration generateLocalized(String system, String userInput,
                                           CurrentUser actor, String feature,
                                           boolean cacheable, String lang) {
        if (!properties.isEnabled()) {
            record(actor, feature, "DISABLED", "AI_DISABLED", 0L);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_DISABLED",
                    "L'assistant IA est temporairement désactivé");
        }
        if (!featureEnabled(feature)) {
            record(actor, feature, "DISABLED", "FEATURE_DISABLED", 0L);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_FEATURE_DISABLED",
                    "Cette fonctionnalité IA est désactivée");
        }
        String clean = sanitize(userInput);
        if (clean.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Contenu vide");
        }
        int perMinute = actor.hasRank(User.Role.MODERATOR)
                ? properties.getDailyQuotaStaff()
                : actor.hasRank(User.Role.CREATOR)
                        ? properties.getRateLimitCreatorPerMinute()
                        : properties.getRateLimitUserPerMinute();
        var verdict = rateLimits.tryConsume("ai-" + feature, "user:" + actor.id(),
                Math.max(1, perMinute), Duration.ofMinutes(1));
        if (!verdict.allowed()) {
            record(actor, feature, "RATE_LIMITED", "RATE_LIMITED", 0L);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                    "Trop de demandes IA — réessaie dans "
                            + verdict.retryAfterSeconds() + " s");
        }
        int quota = actor.hasRank(User.Role.MODERATOR) ? properties.getDailyQuotaStaff()
                : actor.hasRank(User.Role.CREATOR) ? properties.getDailyQuotaCreator()
                        : properties.getDailyQuotaUser();
        Instant dayStart = Instant.now().truncatedTo(ChronoUnit.DAYS);
        if (usage.countByUserIdAndStatusAndCreatedAtAfter(actor.id(), "OK", dayStart)
                >= quota) {
            record(actor, feature, "QUOTA_EXCEEDED", "QUOTA_EXCEEDED", 0L);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "AI_QUOTA_EXCEEDED",
                    "Quota IA quotidien atteint (" + quota + " requêtes/jour)");
        }
        AiProvider provider = activeProvider();
        long started = System.currentTimeMillis();
        try {
            AiGeneration generation = provider.generate(new AiPrompt(
                    system, clean, properties.getMaxOutputTokens(), lang));
            long latency = System.currentTimeMillis() - started;
            if (generation.text() == null || generation.text().isBlank()) {
                record(actor, feature, "PROVIDER_ERROR", "EMPTY_RESPONSE", latency);
                throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_EMPTY_RESPONSE",
                        "L'assistant n'a pas pu répondre — réessaie");
            }
            record(actor, feature, generation, latency);
            return generation;
        } catch (AiProviderException ex) {
            long latency = System.currentTimeMillis() - started;
            record(actor, feature, "PROVIDER_ERROR", ex.getCode(), latency);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_PROVIDER_ERROR",
                    "L'assistant est momentanément indisponible");
        }
    }

    /* ─── Feature flags (admin via PUT /api/admin/config) ─────────── */

    /** Clé {@code ai.features.<feature>} : "false" = coupée, défaut = active. */
    public boolean featureEnabled(String feature) {
        try {
            var history = configs.findByConfigKeyOrderByVersionAsc(
                    "ai.features." + feature);
            if (history.isEmpty()) {
                return true;
            }
            return !"false".equalsIgnoreCase(
                    history.get(history.size() - 1).getValue().trim());
        } catch (Exception ex) {
            log.warn("Lecture feature flag IA impossible : {} — activé par défaut",
                    ex.getMessage());
            return true;
        }
    }

    /* ─── Assainissement (jamais de secret vers le provider) ──────── */

    /**
     * Nettoie une DONNÉE utilisateur : retire les motifs sensibles
     * (JWT, clés, secrets), borne la taille. Le texte reste une
     * donnée — jamais promu en instruction.
     */
    public String sanitize(String input) {
        if (input == null) {
            return "";
        }
        String clean = input
                // JWT / tokens porteurs
                .replaceAll("(?i)\\b(eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}[.A-Za-z0-9_-]*)", "[token-retiré]")
                // paires clé=secret évidentes
                .replaceAll("(?i)(api[_-]?key|secret|password|mot[_-]?de[_-]?passe|stream[_-]?key)\\s*[:=]\\s*\\S+", "$1=[retiré]")
                // clés hex/longues (stream keys…)
                .replaceAll("\\b[a-fA-F0-9]{32,}\\b", "[clé-retirée]");
        int max = Math.max(200, properties.getMaxPromptChars());
        clean = clean.trim();
        if (clean.length() > max) {
            clean = clean.substring(0, max).trim();
        }
        return clean;
    }

    /* ─── Cache Redis (réponses réutilisables, repli : sans cache) ── */

    private String cacheKey(String feature, String system, String clean) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    (feature + "|" + system + "|" + clean)
                            .getBytes(StandardCharsets.UTF_8));
            return "ai:cache:" + feature + ":"
                    + HexFormat.of().formatHex(hash).substring(0, 32);
        } catch (Exception ex) {
            return "ai:cache:" + feature + ":" + clean.hashCode();
        }
    }

    private String readCache(String feature, String system, String clean) {
        try {
            return redis.opsForValue().get(cacheKey(feature, system, clean));
        } catch (Exception ex) {
            return null; // repli : sans cache, jamais bloquant
        }
    }

    private void writeCache(String feature, String system, String clean, String text) {
        try {
            redis.opsForValue().set(cacheKey(feature, system, clean), text,
                    properties.getCacheTtl() == null
                            ? Duration.ofMinutes(30) : properties.getCacheTtl());
        } catch (Exception ex) {
            log.debug("Écriture cache IA impossible : {}", ex.getMessage());
        }
    }

    /* ─── Enregistrement usage (toujours, succès comme échecs) ────── */

    private void record(CurrentUser actor, String feature, String status,
                        String errorCode, long latencyMs) {
        try {
            usage.save(AiUsage.record(providerName(), modelName(),
                    actor.id(), feature, null, null, latencyMs, status,
                    errorCode));
        } catch (Exception ex) {
            log.warn("Enregistrement usage IA impossible : {}", ex.getMessage());
        }
    }

    private void record(CurrentUser actor, String feature,
                        AiGeneration generation, long latencyMs) {
        try {
            usage.save(AiUsage.record(providerName(), modelName(),
                    actor.id(), feature, generation.inputTokens(),
                    generation.outputTokens(), latencyMs, "OK", null));
        } catch (Exception ex) {
            log.warn("Enregistrement usage IA impossible : {}", ex.getMessage());
        }
    }

    /** Fonctionnalités exposées (pour l'admin + le frontend). */
    public List<String> features() {
        return List.of("titles", "description", "tags", "vod-summary",
                "clip-suggestions", "analytics", "assistant", "search-assist",
                "moderation");
    }
}
