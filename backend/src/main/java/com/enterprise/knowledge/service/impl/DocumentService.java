package com.enterprise.knowledge.service.impl;

import com.enterprise.knowledge.domain.Document;
import com.enterprise.knowledge.domain.User;
import com.enterprise.knowledge.dto.response.DocumentResponse;
import com.enterprise.knowledge.exception.ResourceNotFoundException;
import com.enterprise.knowledge.mapper.DocumentMapper;
import com.enterprise.knowledge.repository.DocumentRepository;
import com.enterprise.knowledge.service.DocumentIngestionService;
import com.enterprise.knowledge.service.StorageService;
import com.enterprise.knowledge.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Document management service.
 * Handles upload, retrieval, deletion, and re-indexing of documents.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final StorageService storageService;
    private final DocumentIngestionService ingestionService;
    private final SecurityUtils securityUtils;
    private final DocumentMapper documentMapper;

    /**
     * Upload and process a document.
     */
    @Transactional
    public DocumentResponse uploadDocument(
        MultipartFile file,
        String title,
        String description,
        String[] tags,
        String category
    ) throws IOException {
        User user = securityUtils.getCurrentUser();
        
        log.info("Processing document upload: {} ({} bytes) by user {}", 
            file.getOriginalFilename(), file.getSize(), user.getEmail());

        // Calculate SHA-256 checksum
        String checksum = calculateChecksum(file);
        
        // Check for duplicates
        if (documentRepository.existsByChecksumSha256AndDeletedAtIsNull(checksum)) {
            log.warn("Duplicate document detected with checksum: {}", checksum);
            throw new IllegalArgumentException("This document has already been uploaded");
        }

        // Determine file type
        Document.FileType fileType = determineFileType(file.getOriginalFilename());

        // Assign ID before storage so local/S3 paths can use it
        UUID documentId = UUID.randomUUID();

        // Create document entity
        Document document = Document.builder()
            .id(documentId)
            .title(title != null ? title : file.getOriginalFilename())
            .description(description)
            .fileName(file.getOriginalFilename())
            .fileType(fileType)
            .fileSizeBytes(file.getSize())
            .checksumSha256(checksum)
            .uploadedBy(user)
            .status(Document.DocumentStatus.PENDING)
            .tags(tags != null ? tags : new String[0])
            .category(category)
            .build();

        // Upload to storage
        StorageService.StorageMetadata storage = storageService.upload(file, documentId);
        document.setS3Bucket(storage.bucket());
        document.setS3Key(storage.key());

        document = documentRepository.saveAndFlush(document);
        log.info("Document saved with ID: {}", document.getId());

        // Ingestion must start AFTER this transaction commits, otherwise the async
        // worker cannot see the new row (and may race with a regenerated ID).
        final UUID savedId = document.getId();
        final String bucket = storage.bucket();
        final String key = storage.key();
        Runnable startIngestion = () -> {
            try {
                ingestionService.ingestDocument(
                    savedId,
                    storageService.getInputStream(bucket, key)
                );
            } catch (Exception e) {
                log.error("Failed to start document ingestion for {}: {}", savedId, e.getMessage(), e);
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    startIngestion.run();
                }
            });
        } else {
            startIngestion.run();
        }

        return documentMapper.toDocumentResponse(document);
    }

    /**
     * Get paginated list of documents with filters.
     */
    @Transactional(readOnly = true)
    public Page<DocumentResponse> getDocuments(
        Document.DocumentStatus status,
        Document.FileType fileType,
        String search,
        Pageable pageable
    ) {
        String searchPattern = (search == null || search.isBlank())
            ? null
            : "%" + search.toLowerCase() + "%";

        Page<Document> documents = documentRepository.findAllWithFilters(
            status,
            fileType,
            searchPattern,
            pageable
        );
        return documents.map(documentMapper::toDocumentResponse);
    }

    /**
     * Get document by ID.
     */
    @Transactional(readOnly = true)
    public DocumentResponse getDocument(UUID documentId) {
        Document document = documentRepository.findByIdAndDeletedAtIsNull(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("Document", documentId.toString()));
        return documentMapper.toDocumentResponse(document);
    }

    /**
     * Delete document (soft delete).
     */
    @Transactional
    public void deleteDocument(UUID documentId) {
        Document document = documentRepository.findByIdAndDeletedAtIsNull(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("Document", documentId.toString()));

        // Check if user owns this document or is admin
        User currentUser = securityUtils.getCurrentUser();
        if (!document.getUploadedBy().getId().equals(currentUser.getId()) 
            && !SecurityUtils.hasRole("ADMIN")) {
            throw new SecurityException("You don't have permission to delete this document");
        }

        document.softDelete();
        documentRepository.save(document);
        
        log.info("Document {} soft-deleted by user {}", documentId, currentUser.getEmail());
    }

    /**
     * Re-index a document.
     */
    @Transactional
    public void reindexDocument(UUID documentId) throws IOException {
        Document document = documentRepository.findByIdAndDeletedAtIsNull(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("Document", documentId.toString()));

        log.info("Starting re-indexing for document: {}", documentId);

        document.setStatus(Document.DocumentStatus.PENDING);
        documentRepository.save(document);

        ingestionService.reindexDocument(
            documentId,
            storageService.getInputStream(document.getS3Bucket(), document.getS3Key())
        );
    }

    /**
     * Generate pre-signed download URL.
     */
    public String getDownloadUrl(UUID documentId) {
        Document document = documentRepository.findByIdAndDeletedAtIsNull(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("Document", documentId.toString()));

        return storageService.generateDownloadUrl(document.getS3Bucket(), document.getS3Key());
    }

    /**
     * Calculate SHA-256 checksum of file.
     */
    private String calculateChecksum(MultipartFile file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(file.getBytes());
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Determine file type from filename extension.
     */
    private Document.FileType determineFileType(String filename) {
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        return switch (extension) {
            case "pdf" -> Document.FileType.PDF;
            case "docx", "doc" -> Document.FileType.DOCX;
            case "txt" -> Document.FileType.TXT;
            case "md", "markdown" -> Document.FileType.MD;
            case "html", "htm" -> Document.FileType.HTML;
            default -> throw new IllegalArgumentException("Unsupported file type: " + extension);
        };
    }
}
