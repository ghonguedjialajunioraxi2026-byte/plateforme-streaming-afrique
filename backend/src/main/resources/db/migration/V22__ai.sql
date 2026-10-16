-- ============================================================
-- V22 — Intelligence artificielle (étape 18)
-- Compteurs d'usage (append-only, sans contenu de prompt) +
-- historique optionnel des conversations d'assistant (supprimable).
-- ============================================================

CREATE TABLE IF NOT EXISTS ai_usage (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider VARCHAR(30) NOT NULL DEFAULT 'stub',
    model VARCHAR(80) NOT NULL DEFAULT 'flick-stub-1',
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    feature VARCHAR(40) NOT NULL DEFAULT 'assistant',
    input_tokens INTEGER,
    output_tokens INTEGER,
    total_tokens INTEGER,
    latency_ms BIGINT,
    status VARCHAR(30) NOT NULL DEFAULT 'OK',
    error_code VARCHAR(60),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_usage_user ON ai_usage (user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_ai_usage_feature ON ai_usage (feature, created_at);
CREATE INDEX IF NOT EXISTS idx_ai_usage_status ON ai_usage (status, created_at);

-- Conversations d'assistant (conservées UNIQUEMENT sur demande :
-- l'utilisateur peut tout supprimer à tout moment).
CREATE TABLE IF NOT EXISTS ai_conversation (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ai_message (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES ai_conversation (id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL DEFAULT 'user',
    content VARCHAR(4000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_conversation_user ON ai_conversation (user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_ai_message_conversation ON ai_message (conversation_id, id);
