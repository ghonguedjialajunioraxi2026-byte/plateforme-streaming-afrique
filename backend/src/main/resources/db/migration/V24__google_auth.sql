-- ============================================================
-- V24__google_auth.sql — authentification "Continuer avec Google".
-- AUCUNE migration existante modifiée (règle 7.x) : uniquement ADD.
--
-- 1. users.google_id : identifiant stable du compte Google (claim "sub"
--    de l'id_token OIDC). NULL = compte LOCAL sans Google lié.
--    Contrainte UNIQUE : un compte Google ne se lie qu'à UN compte FLICK.
--    (PostgreSQL autorise plusieurs NULL : les comptes locaux coexistent.)
-- 2. users.auth_provider : méthode de création du compte
--    ('LOCAL' = email/mot de passe, 'GOOGLE' = créé via Google).
--    Un compte LOCAL peut ensuite LIER son google_id (liaison explicite
--    après login mot de passe) sans changer son provider d'origine.
-- 3. password_hash reste NULLABLE (déjà le cas en V1) : les comptes créés
--    via Google n'ont pas de mot de passe tant qu'ils n'en définissent pas
--    un (via "mot de passe oublié" ou changement connecté).
-- ============================================================

ALTER TABLE users
    ADD COLUMN google_id VARCHAR(255),
    ADD COLUMN auth_provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL';

ALTER TABLE users
    ADD CONSTRAINT uk_users_google_id UNIQUE (google_id);

ALTER TABLE users
    ADD CONSTRAINT ck_users_auth_provider CHECK (auth_provider IN ('LOCAL', 'GOOGLE'));

CREATE INDEX idx_users_google_id ON users (google_id);
