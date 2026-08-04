package com.enterprise.knowledge.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a single semantic chunk of a document with its vector embedding.
 * The embedding column uses the pgvector extension (VECTOR(1536)) and is
 * queried via JPQL native queries for cosine similarity search.
 */
@Entity
@Table(name = "document_chunks")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"document", "embedding"})
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "content_tokens")
    private Integer contentTokens;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(name = "section_title", length = 500)
    private String sectionTitle;

    @Column(name = "start_char_offset")
    private Integer startCharOffset;

    @Column(name = "end_char_offset")
    private Integer endCharOffset;

    /**
     * The 1536-dimensional embedding vector stored as a PostgreSQL vector type.
     * Mapped via hibernate-vector (SqlTypes.VECTOR) so Hibernate can read and
     * write the pgvector column directly; similarity search still uses native
     * queries. The HNSW index on this column enables sub-millisecond ANN search.
     */
    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 1536)
    @Column(columnDefinition = "vector(1536)")
    private float[] embedding;

    @Column(name = "embedding_model", length = 100)
    @Builder.Default
    private String embeddingModel = "text-embedding-3-small";

    @Column(name = "embedding_version", nullable = false)
    @Builder.Default
    private int embeddingVersion = 1;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
