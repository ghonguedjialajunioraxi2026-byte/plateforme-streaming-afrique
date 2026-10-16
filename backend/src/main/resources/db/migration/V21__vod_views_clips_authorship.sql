-- ============================================================
-- V21__vod_views_clips_authorship.sql — étape 15 : engagement + propriété.
--
--   - view_count (videos, clips) : compteur persisté, incrémenté
--     atomiquement (view_count = view_count + 1) après garde anti-abus
--     Redis (un viewer = une vue par fenêtre) ;
--   - created_by_user_id (clips) : compte ayant DÉCOUPÉ le clip (NULL =
--     historique) ; SET NULL à la suppression du compte (le clip survit,
--     la chaîne source reste propriétaire) ;
--   - video_url NULLABLE : une VOD auto créée sans enregistrement naît
--     sans fichier (le worker la marque FAILED, jamais READY sans fichier).
-- ============================================================

ALTER TABLE videos ADD COLUMN view_count BIGINT NOT NULL DEFAULT 0;

ALTER TABLE videos ALTER COLUMN video_url DROP NOT NULL;

ALTER TABLE clips ADD COLUMN view_count BIGINT NOT NULL DEFAULT 0;

ALTER TABLE clips ADD COLUMN created_by_user_id BIGINT
    REFERENCES users (id) ON DELETE SET NULL;

CREATE INDEX idx_clips_created_by ON clips (created_by_user_id);
