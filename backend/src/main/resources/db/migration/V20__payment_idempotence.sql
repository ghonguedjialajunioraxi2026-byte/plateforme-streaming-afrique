-- ============================================================
-- V20__payment_idempotence.sql — étape 14 : idempotence de création +
-- expiration des paiements abandonnés.
--
--   - idempotency_key UNIQUE NULLABLE : la clé renvoyée par le client à
--     chaque retry (NULL = pas de clé ; l'unicité SQL ignore les NULL,
--     identique PostgreSQL/H2) ;
--   - statut EXPIRED : le scheduler fait basculer les INITIATED/PENDING
--     trop anciens (jamais crédités, jamais rejouables vers CONFIRMED).
-- ============================================================

ALTER TABLE payments ADD COLUMN idempotency_key VARCHAR(100);

ALTER TABLE payments ADD CONSTRAINT uk_payments_idempotency UNIQUE (idempotency_key);

ALTER TABLE payments DROP CONSTRAINT ck_payments_status;

ALTER TABLE payments ADD CONSTRAINT ck_payments_status
    CHECK (status IN ('INITIATED', 'PENDING', 'CONFIRMED', 'FAILED',
        'CANCELLED', 'EXPIRED', 'REFUNDED'));
