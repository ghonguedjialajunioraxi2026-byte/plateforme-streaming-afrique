package com.streamingafrique.backend.auth.google;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Paramètres Google {@code app.google.*} (connexion "Continuer avec Google").
 *
 * Flux ID token OIDC (Google Identity Services) : le frontend reçoit un
 * {@code id_token} signé par Google, le backend le VÉRIFIE (signature,
 * émetteur, audience, expiration) puis émet ses PROPRES jetons FLICK.
 *
 *   - {@code client-id} : Client ID OAuth public du projet Google Cloud
 *     (env {@code GOOGLE_CLIENT_ID}). PUBLIC par nature (visible dans le
 *     frontend et la console Google) — ce n'est PAS un secret. Il sert
 *     d'audience attendue : un id_token émis pour UNE AUTRE application
 *     est rejeté.
 *
 * AUCUN Client Secret n'est requis dans ce flux : aucun échange
 * code/token n'a lieu côté serveur. Donc AUCUN secret Google ne vit ni
 * dans le frontend, ni dans Git, ni dans les logs — uniquement ce
 * Client ID public et la clé JWT FLICK existante (env {@code JWT_SECRET}).
 */
@ConfigurationProperties(prefix = "app.google")
public class GoogleProperties {

    /** Client ID OAuth Google (env {@code GOOGLE_CLIENT_ID}). Vide = Google désactivé. */
    private String clientId = "";

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
}
