package com.streamingafrique.backend.ratelimit;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Limites anti-abus (étape 6.27) — UNE limite par famille de routes, jamais une
 * limite unique globale.
 *
 * <pre>
 *   login      POST /api/auth/login|refresh → anti force-brute (par IP)
 *   register   POST /api/users|/api/auth/register|forgot|reset|verify|resend
 *                                          → anti création massive et spam (par IP)
 *   search     GET *?search=...            → anti scraping coûteux (par IP)
 *   messages   POST /api/events/publish    → anti spam temps réel (par compte, sinon IP)
 *   public     autres GET /api/** sensibles → garde-fou lectures (par IP)
 *   default    tout le reste               → filet généreux (par compte, sinon IP)
 * </pre>
 *
 * Chaque limite = N requêtes par fenêtre glissante de fait « fenêtre fixe »
 * (compteur Redis remis à zéro à chaque fenêtre). Fenêtre courte = réaction
 * rapide ; les valeurs par défaut sont VOLONTAIREMENT généreuses pour le
 * développement et les tests — la production les resserre via variables
 * d'environnement (RATE_LIMIT_LOGIN_REQUESTS, …).
 *
 * Surcharges : RATE_LIMIT_ENABLED, RATE_LIMIT_LOGIN_REQUESTS,
 * RATE_LIMIT_LOGIN_WINDOW, etc.
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    /** Interrupteur général (seule porte de sortie, tests inclus). */
    private boolean enabled = true;

    /**
     * Étape 22 / SEC-005 : fait-on confiance à {@code X-Forwarded-For} pour
     * identifier l'IP cliente ? {@code true} = déploiement DERRIÈRE un reverse
     * proxy de confiance (ou tests) ; {@code false} = exposition DIRECTE —
     * seul {@code getRemoteAddr()} (adresse TCP réelle, non falsifiable par
     * le client) est utilisé. Un attaquant pouvant forger l'en-tête
     * contournerait sinon les buckets par IP (rotation d'IP déclarée).
     * Surchage : RATE_LIMIT_TRUST_PROXY.
     */
    private boolean trustProxy = true;

    private Bucket login = new Bucket(30, Duration.ofMinutes(1));
    private Bucket register = new Bucket(20, Duration.ofMinutes(1));
    private Bucket search = new Bucket(300, Duration.ofMinutes(1));
    private Bucket messages = new Bucket(120, Duration.ofMinutes(1));
    private Bucket publicBucket = new Bucket(600, Duration.ofMinutes(1));
    private Bucket defaultBucket = new Bucket(1000, Duration.ofMinutes(1));

    /** Une limite : N requêtes max par fenêtre. */
    public static class Bucket {
        private int requests;
        private Duration window;

        public Bucket() {
        }

        public Bucket(int requests, Duration window) {
            this.requests = requests;
            this.window = window;
        }

        public int getRequests() {
            return requests;
        }

        public void setRequests(int requests) {
            this.requests = requests;
        }

        public Duration getWindow() {
            return window;
        }

        public void setWindow(Duration window) {
            this.window = window;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isTrustProxy() {
        return trustProxy;
    }

    public void setTrustProxy(boolean trustProxy) {
        this.trustProxy = trustProxy;
    }

    public Bucket getLogin() {
        return login;
    }

    public void setLogin(Bucket login) {
        this.login = login;
    }

    public Bucket getRegister() {
        return register;
    }

    public void setRegister(Bucket register) {
        this.register = register;
    }

    public Bucket getSearch() {
        return search;
    }

    public void setSearch(Bucket search) {
        this.search = search;
    }

    public Bucket getMessages() {
        return messages;
    }

    public void setMessages(Bucket messages) {
        this.messages = messages;
    }

    public Bucket getPublicBucket() {
        return publicBucket;
    }

    public void setPublicBucket(Bucket publicBucket) {
        this.publicBucket = publicBucket;
    }

    public Bucket getDefaultBucket() {
        return defaultBucket;
    }

    public void setDefaultBucket(Bucket defaultBucket) {
        this.defaultBucket = defaultBucket;
    }
}
