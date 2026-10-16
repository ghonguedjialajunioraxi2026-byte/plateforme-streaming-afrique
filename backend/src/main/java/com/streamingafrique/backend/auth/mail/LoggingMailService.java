package com.streamingafrique.backend.auth.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Expéditeur de DÉVELOPPEMENT/TEST (étape 08) — actif sur tous les profils
 * SAUF prod (aucun SMTP requis en local/CI).
 *
 * Rejoue le lien au log (INFO, préfixé DEV-ONLY) pour tester les flux
 * verify-email / reset-password sans serveur SMTP. En production, ce bean
 * n'existe PAS (voir {@link NoOpMailService}) : aucun token ne peut finir
 * dans les logs prod — règle absolue de l'étape 08 §16.
 */
@Service
@Profile("!prod")
public class LoggingMailService implements MailService {

    private static final Logger log = LoggerFactory.getLogger(LoggingMailService.class);

    @Override
    public void sendVerificationEmail(String toEmail, String verificationLink) {
        log.info("MAIL DEV-ONLY vers {} : lien de vérification = {}", toEmail, verificationLink);
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        log.info("MAIL DEV-ONLY vers {} : lien de récupération = {}", toEmail, resetLink);
    }
}
