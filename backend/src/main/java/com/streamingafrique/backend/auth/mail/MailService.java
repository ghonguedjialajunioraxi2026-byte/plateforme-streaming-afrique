package com.streamingafrique.backend.auth.mail;

/**
 * Envoi d'emails liés à l'authentification (étape 08).
 *
 * Contrats minimaux : vérification d'adresse et récupération de mot de passe.
 * L'implémentation dépend du profil : en développement/test, un expéditeur
 * de CONSOLE rejoue le lien (aucun SMTP requis) ; en production, aucun
 * envoi réel tant que le SMTP n'est pas configuré (fail-safe documenté,
 * jamais de token en log).
 */
public interface MailService {

    /** Lien de vérification d'adresse (token à usage unique, 24 h). */
    void sendVerificationEmail(String toEmail, String verificationLink);

    /** Lien de récupération de mot de passe (token à usage unique, 1 h). */
    void sendPasswordResetEmail(String toEmail, String resetLink);
}
