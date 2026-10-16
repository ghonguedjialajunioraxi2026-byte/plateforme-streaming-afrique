package com.streamingafrique.backend.auth.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Expéditeur de PRODUCTION (étape 08) — SMTP non configuré à cette étape.
 *
 * Fail-safe : les flux verify-email / reset-password RESTENT fonctionnels
 * (tokens créés, réponses génériques), mais aucun email ne part et AUCUN
 * token n'est journalisé (ni lien, ni adresse ciblée au-delà du strict
 * nécessaire). Le branchement SMTP réel (MAIL_HOST/PORT/USERNAME/PASSWORD,
 * voir .env.example) fera l'objet d'une étape dédiée.
 */
@Service
@Profile("prod")
public class NoOpMailService implements MailService {

    private static final Logger log = LoggerFactory.getLogger(NoOpMailService.class);

    @Override
    public void sendVerificationEmail(String toEmail, String verificationLink) {
        log.warn("SMTP non configuré : email de vérification NON envoyé (prod)");
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        log.warn("SMTP non configuré : email de récupération NON envoyé (prod)");
    }
}
