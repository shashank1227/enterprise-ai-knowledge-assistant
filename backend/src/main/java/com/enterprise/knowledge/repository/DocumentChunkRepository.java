package com.enterprise.knowledge.repository;

import com.enterprise.knowledge.domain.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

    List<DocumentChunk> findAllByDocumentIdOrderByChunkIndex(UUID documentId);

    void deleteAllByDocumentId(UUID documentId);

    long countByDocumentId(UUID documentId);

    @Query("SELECT COUNT(dc) FROM DocumentChunk dc")
    long countAll();

    /**
     * Pure vector similarity search using pgvector cosine distance operator <=>
     * Returns top-K chunks ordered by cosine similarity (1 - distance).
     * The cast to ::vector is required for the pgvector operator to work with
     * a JDBC parameter passed as a string in the format '[0.1,0.2,...]'.
     */
    @Query(value = """
        SELECT dc.id, dc.document_id, dc.content, dc.section_title, dc.page_number,
               dc.chunk_index, dc.embedding_model,
               1 - (dc.embedding <=> CAST(:embedding AS vector)) AS score
        FROM document_chunks dc
        JOIN documents d ON d.id = dc.document_id
        WHERE d.status = 'INDEXED'
          AND d.deleted_at IS NULL
        ORDER BY dc.embedding <=> CAST(:embedding AS vector)
        LIMIT :topK
        """, nativeQuery = true)
    List<Object[]> vectorSearch(
        @Param("embedding") String embeddingJson,
        @Param("topK") int topK
    );

    /**
     * Full-text keyword search using PostgreSQL tsvector.
     */
    @Query(value = """
        SELECT dc.id, dc.document_id, dc.content, dc.section_title, dc.page_number,
               dc.chunk_index, dc.embedding_model,
               ts_rank_cd(to_tsvector('english', dc.content), plainto_tsquery('english', :query)) AS score
        FROM document_chunks dc
        JOIN documents d ON d.id = dc.document_id
        WHERE d.status = 'INDEXED'
          AND d.deleted_at IS NULL
          AND to_tsvector('english', dc.content) @@ plainto_tsquery('english', :query)
        ORDER BY score DESC
        LIMIT :topK
        """, nativeQuery = true)
    List<Object[]> keywordSearch(
        @Param("query") String query,
        @Param("topK") int topK
    );

    /**
     * Hybrid search combining vector similarity + BM25 text search via
     * Reciprocal Rank Fusion (RRF). Calls the stored function defined in V1 migration.
     */
    @Query(value = """
        SELECT chunk_id, document_id, content, section_title, page_number,
               vector_score, text_score, hybrid_score
        FROM hybrid_search(
            CAST(:embedding AS vector),
            :queryText,
            :topK,
            60
        )
        """, nativeQuery = true)
    List<Object[]> hybridSearch(
        @Param("embedding")  String embeddingJson,
        @Param("queryText")  String queryText,
        @Param("topK")       int topK
    );

    @Modifying
    @Query(value = "DELETE FROM document_chunks WHERE document_id = :docId", nativeQuery = true)
    void deleteByDocumentIdNative(@Param("docId") UUID docId);
}
