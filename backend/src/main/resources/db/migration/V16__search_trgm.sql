-- ============================================================
-- V16__search_trgm.sql — recherche floue VOD (7.27, SEULE optimisation
-- retenue). Mesuré sur 100k vidéos (EXPLAIN ANALYZE) : ILIKE '%…%' sans
-- index = 111 ms pire cas (scan complet) → 0,065 ms avec GIN trigramme
-- (×1700), 4,4 Mo pour 15 Mo de table. Cas fréquent inchangé (le planner
-- garde le seq scan quand c'est optimal). Tout le reste (lives 0,07 ms,
-- chat/notifs/follows/tx en Index Only < 0,3 ms) n'a RIEN nécessité :
-- pas d'index sans justification.
-- Index NON CONCURRENTLY volontaire : Flyway exécute en transaction
-- (CONCURRENTLY interdit) ; verrou bref acceptable en V1.
-- Motif streams/clips/chaînes : NON mesuré à volume significatif →
-- différé (même pattern : pg_trgm déjà disponible, une ligne par index).
-- ============================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS gin_videos_title_trgm
    ON videos USING gin (title gin_trgm_ops);
