-- V1__init_schema.sql
-- Initial schema for Enterprise AI Knowledge Assistant

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "vector";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- ── Users ──────────────────────────────────────────────────

CREATE TABLE users (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email             VARCHAR(255) NOT NULL UNIQUE,
    password_hash     VARCHAR(255),
    full_name         VARCHAR(255) NOT NULL,
    avatar_url        VARCHAR(500),
    department        VARCHAR(100),
    job_title         VARCHAR(100),
    is_active         BOOLEAN NOT NULL DEFAULT TRUE,
    is_email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    oauth_provider    VARCHAR(50),
    oauth_subject     VARCHAR(255),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_login_at     TIMESTAMPTZ
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_oauth ON users(oauth_provider, oauth_subject);

CREATE TABLE roles (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name        VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO roles (name, description) VALUES
    ('ADMIN',  'Full system access'),
    ('USER',   'Standard user access'),
    ('VIEWER', 'Read-only access');

CREATE TABLE user_roles (
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id     UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    assigned_by UUID REFERENCES users(id),
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX idx_user_roles_user ON user_roles(user_id);

CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) NOT NULL UNIQUE,
    device_info VARCHAR(255),
    ip_address  INET,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked     BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_hash ON refresh_tokens(token_hash);

-- ── Documents ──────────────────────────────────────────────

CREATE TABLE knowledge_sources (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name        VARCHAR(255) NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    config      JSONB,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE documents (
    id                 UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    title              VARCHAR(500) NOT NULL,
    description        TEXT,
    file_name          VARCHAR(500) NOT NULL,
    file_type          VARCHAR(50) NOT NULL,
    file_size_bytes    BIGINT NOT NULL,
    s3_bucket          VARCHAR(255) NOT NULL,
    s3_key             VARCHAR(1000) NOT NULL,
    checksum_sha256    VARCHAR(64),
    source_id          UUID REFERENCES knowledge_sources(id),
    uploaded_by        UUID NOT NULL REFERENCES users(id),
    status             VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    processing_error   TEXT,
    version            INT NOT NULL DEFAULT 1,
    parent_document_id UUID REFERENCES documents(id),
    tags               TEXT[],
    metadata           JSONB,
    category           VARCHAR(100),
    language           VARCHAR(10) DEFAULT 'en',
    page_count         INT,
    word_count         INT,
    token_count        INT,
    indexed_at         TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at         TIMESTAMPTZ
);

CREATE INDEX idx_documents_uploaded_by ON documents(uploaded_by);
CREATE INDEX idx_documents_status ON documents(status);
CREATE INDEX idx_documents_file_type ON documents(file_type);
CREATE INDEX idx_documents_created_at ON documents(created_at DESC);
CREATE INDEX idx_documents_tags ON documents USING gin(tags);
CREATE INDEX idx_documents_metadata ON documents USING gin(metadata);
CREATE INDEX idx_documents_title_trgm ON documents USING gin(title gin_trgm_ops);

CREATE TABLE document_chunks (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    document_id       UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    chunk_index       INT NOT NULL,
    content           TEXT NOT NULL,
    content_tokens    INT,
    page_number       INT,
    section_title     VARCHAR(500),
    start_char_offset INT,
    end_char_offset   INT,
    embedding         VECTOR(1536),
    embedding_model   VARCHAR(100) DEFAULT 'text-embedding-3-small',
    embedding_version INT NOT NULL DEFAULT 1,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (document_id, chunk_index)
);

CREATE INDEX idx_chunks_document_id ON document_chunks(document_id);
CREATE INDEX idx_chunks_embedding_hnsw ON document_chunks
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
CREATE INDEX idx_chunks_content_fts ON document_chunks
    USING gin(to_tsvector('english', content));
CREATE INDEX idx_chunks_content_trgm ON document_chunks
    USING gin(content gin_trgm_ops);

-- ── Conversations ──────────────────────────────────────────

CREATE TABLE conversations (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title           VARCHAR(500),
    is_pinned       BOOLEAN NOT NULL DEFAULT FALSE,
    is_archived     BOOLEAN NOT NULL DEFAULT FALSE,
    message_count   INT NOT NULL DEFAULT 0,
    last_message_at TIMESTAMPTZ,
    metadata        JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_conversations_user_id ON conversations(user_id);
CREATE INDEX idx_conversations_pinned ON conversations(user_id, is_pinned) WHERE is_pinned = TRUE;
CREATE INDEX idx_conversations_last_message ON conversations(user_id, last_message_at DESC);

CREATE TABLE messages (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    conversation_id   UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    role              VARCHAR(20) NOT NULL,
    content           TEXT NOT NULL,
    tokens_used       INT,
    model_used        VARCHAR(100),
    prompt_tokens     INT,
    completion_tokens INT,
    total_tokens      INT,
    latency_ms        INT,
    finish_reason     VARCHAR(50),
    is_regenerated    BOOLEAN NOT NULL DEFAULT FALSE,
    parent_message_id UUID REFERENCES messages(id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_messages_conversation ON messages(conversation_id, created_at ASC);

CREATE TABLE message_citations (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    message_id      UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    chunk_id        UUID NOT NULL REFERENCES document_chunks(id),
    document_id     UUID NOT NULL REFERENCES documents(id),
    citation_index  INT NOT NULL,
    relevance_score FLOAT NOT NULL,
    excerpt         TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_citations_message ON message_citations(message_id);

CREATE TABLE message_feedback (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    message_id    UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    user_id       UUID NOT NULL REFERENCES users(id),
    rating        SMALLINT,
    comment       TEXT,
    feedback_type VARCHAR(50),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (message_id, user_id)
);

-- ── Analytics & Audit ──────────────────────────────────────

CREATE TABLE query_analytics (
    id                   UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id              UUID REFERENCES users(id),
    query_text           TEXT NOT NULL,
    query_embedding      VECTOR(1536),
    conversation_id      UUID REFERENCES conversations(id),
    message_id           UUID REFERENCES messages(id),
    retrieved_chunk_ids  UUID[],
    top_documents        UUID[],
    retrieval_latency_ms INT,
    llm_latency_ms       INT,
    total_latency_ms     INT,
    prompt_tokens        INT,
    completion_tokens    INT,
    total_tokens         INT,
    estimated_cost_usd   DECIMAL(10, 6),
    model_used           VARCHAR(100),
    had_results          BOOLEAN,
    result_count         INT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_analytics_user ON query_analytics(user_id);
CREATE INDEX idx_analytics_created ON query_analytics(created_at DESC);

CREATE TABLE audit_logs (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID REFERENCES users(id),
    action      VARCHAR(100) NOT NULL,
    resource    VARCHAR(100),
    resource_id UUID,
    details     JSONB,
    ip_address  INET,
    user_agent  VARCHAR(500),
    success     BOOLEAN NOT NULL DEFAULT TRUE,
    error_msg   TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_user ON audit_logs(user_id);
CREATE INDEX idx_audit_action ON audit_logs(action);
CREATE INDEX idx_audit_created ON audit_logs(created_at DESC);

-- ── System Config ──────────────────────────────────────────

CREATE TABLE system_config (
    key         VARCHAR(100) PRIMARY KEY,
    value       TEXT NOT NULL,
    description VARCHAR(500),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by  UUID REFERENCES users(id)
);

INSERT INTO system_config (key, value, description) VALUES
    ('max_upload_size_mb',    '50',                     'Maximum document upload size in MB'),
    ('max_chunk_size_tokens', '512',                    'Maximum tokens per document chunk'),
    ('chunk_overlap_tokens',  '50',                     'Overlap tokens between chunks'),
    ('top_k_retrieval',       '5',                      'Chunks to retrieve per query'),
    ('embedding_model',       'text-embedding-3-small', 'OpenAI embedding model'),
    ('chat_model',            'gpt-4.1',                'OpenAI chat model'),
    ('max_context_tokens',    '8000',                   'Max context tokens for LLM'),
    ('rate_limit_rpm',        '60',                     'Requests per minute per user');

-- ── Triggers ──────────────────────────────────────────────

CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION update_updated_at();
CREATE TRIGGER trg_documents_updated_at
    BEFORE UPDATE ON documents FOR EACH ROW EXECUTE FUNCTION update_updated_at();
CREATE TRIGGER trg_conversations_updated_at
    BEFORE UPDATE ON conversations FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE OR REPLACE FUNCTION increment_conversation_message_count()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE conversations
    SET message_count = message_count + 1, last_message_at = NEW.created_at
    WHERE id = NEW.conversation_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_message_count
    AFTER INSERT ON messages FOR EACH ROW
    EXECUTE FUNCTION increment_conversation_message_count();

-- ── Hybrid Search Function ─────────────────────────────────

CREATE OR REPLACE FUNCTION hybrid_search(
    query_embedding VECTOR(1536),
    query_text      TEXT,
    match_count     INT DEFAULT 5,
    rrf_k           INT DEFAULT 60
)
RETURNS TABLE (
    chunk_id        UUID,
    document_id     UUID,
    content         TEXT,
    section_title   VARCHAR,
    page_number     INT,
    vector_score    FLOAT,
    text_score      FLOAT,
    hybrid_score    FLOAT
) AS $$
BEGIN
    RETURN QUERY
    WITH vector_results AS (
        SELECT dc.id, dc.document_id, dc.content, dc.section_title, dc.page_number,
               1 - (dc.embedding <=> query_embedding) AS score,
               ROW_NUMBER() OVER (ORDER BY dc.embedding <=> query_embedding ASC) AS rank
        FROM document_chunks dc
        JOIN documents d ON d.id = dc.document_id
        WHERE d.status = 'INDEXED' AND d.deleted_at IS NULL
        ORDER BY dc.embedding <=> query_embedding
        LIMIT match_count * 3
    ),
    text_results AS (
        SELECT dc.id, dc.document_id, dc.content, dc.section_title, dc.page_number,
               ts_rank_cd(to_tsvector('english', dc.content), plainto_tsquery('english', query_text)) AS score,
               ROW_NUMBER() OVER (ORDER BY ts_rank_cd(to_tsvector('english', dc.content), plainto_tsquery('english', query_text)) DESC) AS rank
        FROM document_chunks dc
        JOIN documents d ON d.id = dc.document_id
        WHERE d.status = 'INDEXED' AND d.deleted_at IS NULL
          AND to_tsvector('english', dc.content) @@ plainto_tsquery('english', query_text)
        ORDER BY score DESC
        LIMIT match_count * 3
    )
    SELECT COALESCE(v.id, t.id),
           COALESCE(v.document_id, t.document_id),
           COALESCE(v.content, t.content),
           COALESCE(v.section_title, t.section_title),
           COALESCE(v.page_number, t.page_number),
           COALESCE(v.score, 0.0),
           COALESCE(t.score, 0.0),
           COALESCE(1.0 / (rrf_k + v.rank), 0) + COALESCE(1.0 / (rrf_k + t.rank), 0)
    FROM vector_results v
    FULL OUTER JOIN text_results t ON v.id = t.id
    ORDER BY 8 DESC
    LIMIT match_count;
END;
$$ LANGUAGE plpgsql;
