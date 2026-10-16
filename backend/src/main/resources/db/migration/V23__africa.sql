-- ============================================================
-- V23 — Africa+ (étape 19) : pays configurables, moyens de paiement
-- par pays, langue des VOD, préférences régionales du profil.
-- Aucun pays hard-codé dans le code : tout vient de `countries`.
-- Aucun taux de change inventé (aucune table de taux seedée).
-- ============================================================

-- Pays supportés (données de configuration, modifiables par l'admin).
CREATE TABLE IF NOT EXISTS countries (
    code VARCHAR(2) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    region VARCHAR(20) NOT NULL DEFAULT 'WEST',
    phone_code VARCHAR(8),
    default_currency VARCHAR(3) NOT NULL,
    default_locale VARCHAR(10) NOT NULL,
    supported_languages VARCHAR(100) NOT NULL DEFAULT 'fr,en',
    timezone VARCHAR(60) NOT NULL DEFAULT 'UTC',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Moyens de paiement par pays (le prestataire réel n'est ACTIF que
-- lorsqu'une intégration officielle est câblée ; en attendant, la
-- ligne reste active=FALSE et documente la préparation).
CREATE TABLE IF NOT EXISTS country_payment_methods (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    country_code VARCHAR(2) NOT NULL REFERENCES countries (code) ON DELETE CASCADE,
    provider VARCHAR(30) NOT NULL,
    method VARCHAR(30) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_country_provider_method UNIQUE (country_code, provider, method)
);

CREATE INDEX IF NOT EXISTS idx_cpm_country_active
    ON country_payment_methods (country_code, active, sort_order);

-- Langue déclarée d'une VOD (NULL = non renseignée, jamais bloquant).
ALTER TABLE videos ADD COLUMN IF NOT EXISTS language VARCHAR(10);

-- Préférences régionales du profil (tout NULL = défauts plateforme).
ALTER TABLE profiles ADD COLUMN IF NOT EXISTS timezone VARCHAR(60);
ALTER TABLE profiles ADD COLUMN IF NOT EXISTS preferred_currency VARCHAR(3);

CREATE INDEX IF NOT EXISTS idx_profiles_country ON profiles (country_code);
CREATE INDEX IF NOT EXISTS idx_videos_language ON videos (language);
CREATE INDEX IF NOT EXISTS idx_streams_language ON streams (language);

-- ---------- Données de configuration (15 marchés africains) ----------

INSERT INTO countries (code, name, region, phone_code, default_currency, default_locale, supported_languages, timezone, active) VALUES
    ('CM', 'Cameroun', 'CENTRAL', '237', 'XAF', 'fr-CM', 'fr,en', 'Africa/Douala', TRUE),
    ('CI', 'Côte d''Ivoire', 'WEST', '225', 'XOF', 'fr-CI', 'fr', 'Africa/Abidjan', TRUE),
    ('SN', 'Sénégal', 'WEST', '221', 'XOF', 'fr-SN', 'fr,wo', 'Africa/Dakar', TRUE),
    ('GA', 'Gabon', 'CENTRAL', '241', 'XAF', 'fr-GA', 'fr', 'Africa/Libreville', TRUE),
    ('CG', 'Congo', 'CENTRAL', '242', 'XAF', 'fr-CG', 'fr,ln', 'Africa/Brazzaville', TRUE),
    ('CD', 'Congo (RDC)', 'CENTRAL', '243', 'CDF', 'fr-CD', 'fr,ln,sw', 'Africa/Kinshasa', TRUE),
    ('GH', 'Ghana', 'WEST', '233', 'GHS', 'en-GH', 'en', 'Africa/Accra', TRUE),
    ('NG', 'Nigeria', 'WEST', '234', 'NGN', 'en-NG', 'en,ha,yo,ig', 'Africa/Lagos', TRUE),
    ('KE', 'Kenya', 'EAST', '254', 'KES', 'en-KE', 'en,sw', 'Africa/Nairobi', TRUE),
    ('ZA', 'Afrique du Sud', 'SOUTHERN', '27', 'ZAR', 'en-ZA', 'en', 'Africa/Johannesburg', TRUE),
    ('MA', 'Maroc', 'NORTH', '212', 'MAD', 'fr-MA', 'fr,ar', 'Africa/Casablanca', TRUE),
    ('DZ', 'Algérie', 'NORTH', '213', 'DZD', 'fr-DZ', 'fr,ar', 'Africa/Algiers', TRUE),
    ('TN', 'Tunisie', 'NORTH', '216', 'TND', 'fr-TN', 'fr,ar', 'Africa/Tunis', TRUE),
    ('TZ', 'Tanzanie', 'EAST', '255', 'TZS', 'en-TZ', 'en,sw', 'Africa/Dar_es_Salaam', TRUE),
    ('UG', 'Ouganda', 'EAST', '256', 'UGX', 'en-UG', 'en,sw', 'Africa/Kampala', TRUE)
ON CONFLICT (code) DO NOTHING;

-- Moyens préparés par pays (active=FALSE : activation = intégration
-- officielle câblée + identifiants réels ; rien n'est simulé).
INSERT INTO country_payment_methods (country_code, provider, method, currency, active, sort_order) VALUES
    ('CM', 'MTN_MOMO', 'MOBILE_MONEY', 'XAF', FALSE, 1),
    ('CM', 'ORANGE_MONEY', 'MOBILE_MONEY', 'XAF', FALSE, 2),
    ('CM', 'STRIPE', 'CARD', 'XAF', FALSE, 3),
    ('CI', 'WAVE', 'MOBILE_MONEY', 'XOF', FALSE, 1),
    ('CI', 'ORANGE_MONEY', 'MOBILE_MONEY', 'XOF', FALSE, 2),
    ('CI', 'MTN_MOMO', 'MOBILE_MONEY', 'XOF', FALSE, 3),
    ('SN', 'WAVE', 'MOBILE_MONEY', 'XOF', FALSE, 1),
    ('SN', 'ORANGE_MONEY', 'MOBILE_MONEY', 'XOF', FALSE, 2),
    ('KE', 'MPESA', 'MOBILE_MONEY', 'KES', FALSE, 1),
    ('KE', 'STRIPE', 'CARD', 'KES', FALSE, 2),
    ('NG', 'PAYSTACK', 'MOBILE_MONEY', 'NGN', FALSE, 1),
    ('NG', 'FLUTTERWAVE', 'CARD', 'NGN', FALSE, 2),
    ('GH', 'MTN_MOMO', 'MOBILE_MONEY', 'GHS', FALSE, 1),
    ('GH', 'FLUTTERWAVE', 'CARD', 'GHS', FALSE, 2),
    ('ZA', 'STRIPE', 'CARD', 'ZAR', FALSE, 1),
    ('ZA', 'PAYSTACK', 'CARD', 'ZAR', FALSE, 2)
ON CONFLICT (country_code, provider, method) DO NOTHING;
