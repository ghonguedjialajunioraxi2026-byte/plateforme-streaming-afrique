package com.streamingafrique.backend.beta.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Paramètres bêta privée {@code app.beta.*} (étape 24).
 *
 * {@code invite-only} (défaut {@code false}) : quand l'environnement bêta
 * l'active, toute inscription exige un code d'invitation valide lié à
 * l'email — sinon 403. Hors bêta, comportement inchangé.
 */
@ConfigurationProperties(prefix = "app.beta")
public class BetaProperties {

    /** Inscription sur invitation uniquement (environnement bêta). */
    private boolean inviteOnly = false;

    /** Durée de validité d'une invitation (défaut 14 jours). */
    private Duration invitationTtl = Duration.ofDays(14);

    public boolean isInviteOnly() {
        return inviteOnly;
    }

    public void setInviteOnly(boolean inviteOnly) {
        this.inviteOnly = inviteOnly;
    }

    public Duration getInvitationTtl() {
        return invitationTtl;
    }

    public void setInvitationTtl(Duration invitationTtl) {
        this.invitationTtl = invitationTtl;
    }
}
