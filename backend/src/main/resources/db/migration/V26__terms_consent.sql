-- ============================================================
-- V26__terms_consent.sql — consentement CGU (étape 25).
-- AUCUNE migration existante modifiée : uniquement ADD.
--
-- users.terms_accepted_at : horodatage du consentement recueilli à
-- l'inscription (case obligatoire). NULL = compte créé avant la bêta
-- publique (consentement implicite historique, non rétroactif).
-- ============================================================

ALTER TABLE users
    ADD COLUMN terms_accepted_at TIMESTAMPTZ;
