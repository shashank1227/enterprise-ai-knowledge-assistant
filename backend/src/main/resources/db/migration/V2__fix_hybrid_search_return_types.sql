-- Fix hybrid_search: the RETURNS TABLE declares FLOAT (double precision) score
-- columns, but ts_rank_cd() returns real and the RRF arithmetic yields numeric,
-- causing "structure of query does not match function result type" at runtime.
-- Cast all score columns explicitly to double precision.

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
           COALESCE(v.score, 0.0)::DOUBLE PRECISION,
           COALESCE(t.score, 0.0)::DOUBLE PRECISION,
           (COALESCE(1.0 / (rrf_k + v.rank), 0) + COALESCE(1.0 / (rrf_k + t.rank), 0))::DOUBLE PRECISION
    FROM vector_results v
    FULL OUTER JOIN text_results t ON v.id = t.id
    ORDER BY 8 DESC
    LIMIT match_count;
END;
$$ LANGUAGE plpgsql;
