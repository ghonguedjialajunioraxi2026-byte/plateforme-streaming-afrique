package com.streamingafrique.backend.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration WEB globale du backend.
 *
 *  1. CORS : permet au frontend React (Vite, dev sur http://localhost:5173)
 *     d'appeler l'API. Origines injectées par profil (voir application-*.yml) :
 *     jamais de succursale "*" tant qu'on utilise des credentials.
 *
 *  2. Fichiers statiques /uploads/** : les images téléversées sont servies
 *     depuis le dossier de stockage configurable (app.storage.location).
 *     Leurs noms étant des UUID uniques, le cache navigateur est maximal
 *     (365 jours, public) — un avatar modifié obtient une nouvelle URL.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;
    private final String storageLocation;

    /**
     * Lit la propriété app.cors.allowed-origins (ex. "https://a.com,https://b.com")
     * et la découpe en liste d'origines propres.
     */
    public WebConfig(@Value("${app.cors.allowed-origins}") String origins,
                     StorageProperties storageProperties) {
        this.allowedOrigins = Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
        this.storageLocation = storageProperties.getLocation();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                // Origines précisées, origine par origine (pas "*" avec credentials)
                .allowedOrigins(allowedOrigins)
                // Méthodes HTTP autorisées (+ OPTIONS pour les preflight navigateur)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                // En-têtes autorisés (Authorization préparera JWT plus tard)
                .allowedHeaders("Content-Type", "Accept", "Authorization", "X-Requested-With")
                // fetch(..., {credentials:'include'}) : cookies/sessions autorisés
                .allowCredentials(true)
                // Cache du preflight OPTIONS pendant 1 h (perf)
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path base = Paths.get(storageLocation).toAbsolutePath().normalize();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(base.toUri().toString())
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic());
    }
}