package com.streamingafrique.backend.payment.provider;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.streamingafrique.backend.exception.ApiException;
import com.streamingafrique.backend.payment.config.PaymentProperties;
import com.streamingafrique.backend.payment.model.Payment;

/**
 * Registre des prestataires (étape 14, §7).
 *
 * Aujourd'hui : en mode sandbox (dev/test, défaut), TOUS les prestataires
 * routent vers le simulé — aucune API réelle n'est inventée (§70). Câbler un
 * vrai fournisseur = ajouter son implémentation {@link PaymentProvider} et
 * l'enregistrer ici pour son enum (le reste du système ne change pas).
 * En production (sandbox désactivé), un prestataire sans implémentation réelle
 * est REFUSÉ explicitement (pas de paiement silencieux vers un mock).
 */
@Component
public class PaymentProviderRegistry {

    private static final Logger log = LoggerFactory.getLogger(PaymentProviderRegistry.class);

    private final PaymentProperties properties;
    private final SandboxPaymentProvider sandbox;
    private final Map<Payment.Provider, PaymentProvider> providers = new EnumMap<>(Payment.Provider.class);

    public PaymentProviderRegistry(PaymentProperties properties, SandboxPaymentProvider sandbox,
                                   List<PaymentProvider> all) {
        this.properties = properties;
        this.sandbox = sandbox;
        for (Payment.Provider provider : Payment.Provider.values()) {
            PaymentProvider real = all.stream()
                    .filter(p -> !p.key().equals(SandboxPaymentProvider.KEY)
                            && p.key().equalsIgnoreCase(provider.name()))
                    .findFirst()
                    .orElse(null);
            if (real != null) {
                providers.put(provider, real);
            } else if (properties.isSandboxEnabled()) {
                providers.put(provider, sandbox);
            }
        }
    }

    /** Prestataire pour cet enum — 400 si provider inconnu, 503 si non câblé en prod. */
    public PaymentProvider resolve(Payment.Provider provider) {
        if (provider == null) {
            throw ApiException.badRequest("Prestataire obligatoire");
        }
        PaymentProvider resolved = providers.get(provider);
        if (resolved == null) {
            log.warn("Prestataire {} demandé mais aucune implémentation réelle (sandbox désactivé)",
                    provider);
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "PROVIDER_UNAVAILABLE",
                    "Prestataire " + provider + " indisponible pour le moment");
        }
        return resolved;
    }

    /** Résolution par nom d'URL (webhook /{provider}) — 404 si inconnu. */
    public PaymentProvider resolveByName(String name) {
        if (name == null) {
            throw ApiException.notFound("Prestataire inconnu");
        }
        try {
            return resolve(Payment.Provider.valueOf(name.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw ApiException.notFound("Prestataire inconnu : " + name);
        }
    }

    /** Catalogue exposé au frontend : prestataires et moyens EFFECTIVEMENT disponibles. */
    public List<ProviderInfo> catalog() {
        return Arrays.stream(Payment.Provider.values())
                .filter(providers::containsKey)
                .map(p -> new ProviderInfo(p.name(), providers.get(p).supportedMethods().stream()
                        .map(Enum::name).sorted().toList(), isSandboxed(p)))
                .toList();
    }

    /** Fiche catalogue d'un prestataire disponible. */
    public record ProviderInfo(String provider, List<String> methods, boolean sandbox) {
    }

    private boolean isSandboxed(Payment.Provider provider) {
        return properties.isSandboxEnabled() && providers.get(provider) == sandbox;
    }
}
