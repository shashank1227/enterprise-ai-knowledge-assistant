package com.enterprise.knowledge.repository;

import com.enterprise.knowledge.domain.Document;
import com.enterprise.knowledge.domain.Document.DocumentStatus;
import com.enterprise.knowledge.domain.Document.FileType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    /**
     * Fetch documents with optional filters. Excludes soft-deleted records.
     */
    @Query("""
        SELECT d FROM Document d
        WHERE d.deletedAt IS NULL
          AND (:status IS NULL OR d.status = :status)
          AND (:fileType IS NULL OR d.fileType = :fileType)
          AND (:searchPattern IS NULL OR
               LOWER(d.title) LIKE :searchPattern OR
               LOWER(COALESCE(d.description, '')) LIKE :searchPattern)
        """)
    Page<Document> findAllWithFilters(
        @Param("status") DocumentStatus status,
        @Param("fileType") FileType fileType,
        @Param("searchPattern") String searchPattern,
        Pageable pageable
    );

    Optional<Document> findByIdAndDeletedAtIsNull(UUID id);

    @Query("SELECT COUNT(d) FROM Document d WHERE d.status = :status AND d.deletedAt IS NULL")
    long countByStatus(@Param("status") DocumentStatus status);

    @Query("SELECT COUNT(d) FROM Document d WHERE d.deletedAt IS NULL")
    long countActive();

    @Query("SELECT SUM(d.fileSizeBytes) FROM Document d WHERE d.deletedAt IS NULL")
    Long sumFileSizeBytes();

    List<Document> findAllByStatusAndDeletedAtIsNull(DocumentStatus status);

    @Modifying
    @Query("UPDATE Document d SET d.status = :status, d.processingError = :error WHERE d.id = :id")
    void updateStatus(
        @Param("id")     UUID id,
        @Param("status") DocumentStatus status,
        @Param("error")  String error
    );

    /**
     * Check for duplicate uploads by SHA-256 checksum.
     */
    boolean existsByChecksumSha256AndDeletedAtIsNull(String checksum);
}
