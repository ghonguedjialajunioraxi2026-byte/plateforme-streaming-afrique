-- ============================================================
-- V18__streaming.sql — étape 10 : index d'ingest temps réel.
--
-- Aucune colonne ajoutée : stream_key_hash, ingest_protocol,
-- playback_url, peak_viewers existent depuis V4 (prévus pour
-- cette intégration). On ajoute seulement les index nécessaires
-- à la résolution de clé au moment de la publication RTMP
-- (lookup par empreinte à chaque on_publish SRS) et à la page
-- "en direct" (statut LIVE, non supprimés).
-- ============================================================

-- Résolution ingest : on_publish cherche le direct par empreinte
-- de clé (hash SHA-256 hex). Index simple, pas d'unicité : la
-- rotation invalide l'ancienne clé (hash remplacé), l'unicité est
-- garantie par le générateur cryptographique (256 bits).
CREATE INDEX IF NOT EXISTS idx_streams_key_hash
    ON streams (stream_key_hash);

-- Page "en direct" : seuls les directs LIVE non masqués.
-- Index partiel : les lignes ENDED/SCHEDULED ne le chargent pas.
CREATE INDEX IF NOT EXISTS idx_streams_live
    ON streams (started_at DESC)
    WHERE status = 'LIVE' AND deleted = false;
