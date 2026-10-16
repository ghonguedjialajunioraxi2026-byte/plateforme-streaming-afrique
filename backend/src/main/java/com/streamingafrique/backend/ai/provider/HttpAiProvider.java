package com.streamingafrique.backend.ai.provider;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamingafrique.backend.ai.config.AiProperties;

/**
 * Provider HTTP OpenAI-compatible (OpenAI, Ollama, …​).
 *
 * Actif UNIQUEMENT si {@code ai.provider=http} + {@code base-url}
 * renseignée. La clé API vient de l'environnement
 * ({@code AI_API_KEY}) : jamais loguée, jamais retournée au client.
 * Échec distant → {@link AiProviderException} (le service dégrade
 * poliment, sans inventer de réponse).
 */
@Component
public class HttpAiProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(HttpAiProvider.class);

    private final AiProperties properties;
    private final RestTemplate rest;
    private final ObjectMapper mapper = new ObjectMapper();

    public HttpAiProvider(AiProperties properties, RestTemplateBuilder builder) {
        this.properties = properties;
        Duration timeout = properties.getTimeout() == null
                ? Duration.ofSeconds(20) : properties.getTimeout();
        this.rest = builder
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .build();
    }

    /** Disponible = configuré pour du distant (sinon le stub répond). */
    public boolean available() {
        return "http".equalsIgnoreCase(properties.getProvider())
                && properties.getBaseUrl() != null
                && !properties.getBaseUrl().isBlank();
    }

    @Override
    public String name() {
        return "http";
    }

    @Override
    public AiGeneration generate(AiPrompt prompt) throws AiProviderException {
        if (!available()) {
            throw new AiProviderException("PROVIDER_NOT_CONFIGURED",
                    "Fournisseur distant non configuré");
        }
        String url = properties.getBaseUrl().replaceAll("/$", "")
                + "/chat/completions";
        Map<String, Object> body = Map.of(
                "model", properties.getModel(),
                "max_tokens", Math.max(64, prompt.maxOutputTokens()),
                "messages", List.of(
                        Map.of("role", "system", "content", prompt.system()),
                        Map.of("role", "user", "content", prompt.user())));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // Clé uniquement en en-tête, jamais en log ni en base.
        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            headers.setBearerAuth(properties.getApiKey());
        }
        try {
            String raw = rest.postForObject(URI.create(url),
                    new HttpEntity<>(body, headers), String.class);
            String text = extractText(raw);
            if (text == null || text.isBlank()) {
                throw new AiProviderException("MALFORMED_RESPONSE",
                        "Réponse vide ou illisible du fournisseur");
            }
            return AiGeneration.of(text.trim());
        } catch (ResourceAccessException ex) {
            log.warn("Fournisseur IA injoignable : {}", ex.getMessage());
            throw new AiProviderException("PROVIDER_UNAVAILABLE",
                    "Fournisseur IA injoignable", ex);
        } catch (AiProviderException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Échec d'appel IA : {}", ex.getMessage());
            throw new AiProviderException("PROVIDER_ERROR",
                    "Échec du fournisseur IA", ex);
        }
    }

    /** Extrait choices[0].message.content (format OpenAI). */
    private String extractText(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode root = mapper.readTree(raw);
            JsonNode content = root.path("choices").path(0)
                    .path("message").path("content");
            if (content.isMissingNode() || content.isNull()) {
                return null;
            }
            return content.asText();
        } catch (Exception ex) {
            log.warn("Réponse IA illisible ({} caractères)", raw.length());
            return null;
        }
    }
}
