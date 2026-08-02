package com.enterprise.knowledge.controller;

import com.enterprise.knowledge.domain.Document;
import com.enterprise.knowledge.dto.response.DocumentResponse;
import com.enterprise.knowledge.service.impl.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

/**
 * Document management REST controller.
 * Handles document upload, retrieval, deletion, and re-indexing.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    /**
     * Upload a single document.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<DocumentResponse> uploadDocument(
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "title", required = false) String title,
        @RequestParam(value = "description", required = false) String description,
        @RequestParam(value = "tags", required = false) String[] tags,
        @RequestParam(value = "category", required = false) String category
    ) throws IOException {
        
        log.info("Document upload request: {}", file.getOriginalFilename());
        
        DocumentResponse response = documentService.uploadDocument(
            file,
            title,
            description,
            tags,
            category
        );
        
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Upload multiple documents.
     */
    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> uploadDocuments(
        @RequestParam("files") MultipartFile[] files,
        @RequestParam(value = "tags", required = false) String[] tags,
        @RequestParam(value = "category", required = false) String category
    ) {
        log.info("Bulk upload request: {} files", files.length);
        
        List<DocumentResponse> uploaded = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        
        for (MultipartFile file : files) {
            try {
                DocumentResponse response = documentService.uploadDocument(
                    file,
                    file.getOriginalFilename(),
                    null,
                    tags,
                    category
                );
                uploaded.add(response);
            } catch (Exception e) {
                log.error("Failed to upload {}: {}", file.getOriginalFilename(), e.getMessage());
                failed.add(file.getOriginalFilename() + ": " + e.getMessage());
            }
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("accepted", uploaded.size());
        result.put("rejected", failed.size());
        result.put("documents", uploaded);
        result.put("errors", failed);
        
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }

    /**
     * Get paginated list of documents with optional filters.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'VIEWER', 'ADMIN')")
    public ResponseEntity<Page<DocumentResponse>> getDocuments(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) Document.DocumentStatus status,
        @RequestParam(required = false) Document.FileType fileType,
        @RequestParam(required = false) String search,
        @RequestParam(defaultValue = "createdAt") String sortBy,
        @RequestParam(defaultValue = "desc") String sortDir
    ) {
        Sort.Direction direction = sortDir.equalsIgnoreCase("asc") 
            ? Sort.Direction.ASC 
            : Sort.Direction.DESC;
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        
        Page<DocumentResponse> documents = documentService.getDocuments(
            status,
            fileType,
            search,
            pageable
        );
        
        return ResponseEntity.ok(documents);
    }

    /**
     * Get document by ID.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'VIEWER', 'ADMIN')")
    public ResponseEntity<DocumentResponse> getDocument(@PathVariable UUID id) {
        DocumentResponse document = documentService.getDocument(id);
        return ResponseEntity.ok(document);
    }

    /**
     * Delete document.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<Void> deleteDocument(@PathVariable UUID id) {
        documentService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Re-index a document (regenerate chunks and embeddings).
     */
    @PostMapping("/{id}/reindex")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<Void> reindexDocument(@PathVariable UUID id) throws IOException {
        log.info("Re-index request for document: {}", id);
        documentService.reindexDocument(id);
        return ResponseEntity.accepted().build();
    }

    /**
     * Get pre-signed download URL.
     */
    @GetMapping("/{id}/download")
    @PreAuthorize("hasAnyRole('USER', 'VIEWER', 'ADMIN')")
    public ResponseEntity<Map<String, String>> getDownloadUrl(@PathVariable UUID id) {
        String url = documentService.getDownloadUrl(id);
        Map<String, String> response = Map.of(
            "downloadUrl", url,
            "expiresAt", java.time.Instant.now().plusSeconds(3600).toString()
        );
        return ResponseEntity.ok(response);
    }
}
