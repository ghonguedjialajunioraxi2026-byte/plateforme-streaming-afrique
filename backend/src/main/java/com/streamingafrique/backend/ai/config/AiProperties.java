package com.streamingafrique.backend.ai.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration IA (étape 18) — TOUT secret vient de l'environnement.
 *
 * <pre>
 *   AI_PROVIDER   stub | http        (défaut stub : déterministe, 0 coût)
 *   AI_API_KEY    clé du fournisseur (JAMAIS versionnée, jamais exposée)
 *   AI_MODEL      modèle (ex. " Flick-local " / nom modèle distant)
 *   AI_BASE_URL   base OpenAI-compatible (ex. http://ollama:11434/v1)
 *   AI_ENABLED    interrupteur global
 * </pre>
 *
 * Le navigateur n'appelle JAMAIS un fournisseur directement : React →
 * backend → AIService → AIProvider.
 */
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /** Interrupteur global (false = toute génération refuse poliment). */
    private boolean enabled = true;

    /** stub (local, défaut) ou http (OpenAI-compatible : OpenAI, Ollama…​). */
    private String provider = "stub";

    /** Nom de modèle déclaré (traçabilité usage, jamais une promesse). */
    private String model = "flick-stub-1";

    /** Base URL du fournisseur http (vide = stub). */
    private String baseUrl = "";

    /** Clé API (env uniquement, jamais loguée ni retournée). */
    private String apiKey = "";

    /** Timeout d'appel fournisseur distant. */
    private Duration timeout = Duration.ofSeconds(20);

    /** Taille max d'un prompt utilisateur (caractères, avant troncature). */
    private int maxPromptChars = 2000;

    /** Taille max de sortie demandée (tokens / mots selon provider). */
    private int maxOutputTokens = 500;

    /** Quota quotidien : comptes simples (requêtes/jour). */
    private int dailyQuotaUser = 30;

    /** Quota quotidien : créateurs (requêtes/jour). */
    private int dailyQuotaCreator = 100;

    /** Quota quotidien : modérateurs/admins (requêtes/jour). */
    private int dailyQuotaStaff = 300;

    /** Limite de débit : créateur (requêtes/minute). */
    private int rateLimitCreatorPerMinute = 10;

    /** Limite de débit : utilisateur (requêtes/minute). */
    private int rateLimitUserPerMinute = 10;

    /** TTL du cache Redis des réponses IA réutilisables (FAQ…). */
    private Duration cacheTtl = Duration.ofMinutes(30);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public int getMaxPromptChars() {
        return maxPromptChars;
    }

    public void setMaxPromptChars(int maxPromptChars) {
        this.maxPromptChars = maxPromptChars;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public int getDailyQuotaUser() {
        return dailyQuotaUser;
    }

    public void setDailyQuotaUser(int dailyQuotaUser) {
        this.dailyQuotaUser = dailyQuotaUser;
    }

    public int getDailyQuotaCreator() {
        return dailyQuotaCreator;
    }

    public void setDailyQuotaCreator(int dailyQuotaCreator) {
        this.dailyQuotaCreator = dailyQuotaCreator;
    }

    public int getDailyQuotaStaff() {
        return dailyQuotaStaff;
    }

    public void setDailyQuotaStaff(int dailyQuotaStaff) {
        this.dailyQuotaStaff = dailyQuotaStaff;
    }

    public int getRateLimitCreatorPerMinute() {
        return rateLimitCreatorPerMinute;
    }

    public void setRateLimitCreatorPerMinute(int rateLimitCreatorPerMinute) {
        this.rateLimitCreatorPerMinute = rateLimitCreatorPerMinute;
    }

    public int getRateLimitUserPerMinute() {
        return rateLimitUserPerMinute;
    }

    public void setRateLimitUserPerMinute(int rateLimitUserPerMinute) {
        this.rateLimitUserPerMinute = rateLimitUserPerMinute;
    }

    public Duration getCacheTtl() {
        return cacheTtl;
    }

    public void setCacheTtl(Duration cacheTtl) {
        this.cacheTtl = cacheTtl;
    }
}
