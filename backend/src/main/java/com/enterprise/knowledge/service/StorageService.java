package com.enterprise.knowledge.service;

import com.enterprise.knowledge.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.UUID;

/**
 * Storage service for document file management.
 * Supports both S3 (production) and local filesystem (development).
 * 
 * S3 features:
 * - Server-side encryption
 * - Pre-signed URLs for secure downloads
 * - Automatic content-type detection
 * - Lifecycle policies (configured separately in AWS)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final AppProperties appProperties;

    /**
     * Upload file to storage.
     * @param file The multipart file
     * @param documentId Unique document identifier
     * @return Storage metadata
     */
    public StorageMetadata upload(MultipartFile file, UUID documentId) throws IOException {
        String storageType = appProperties.getStorage().getType();
        
        return switch (storageType) {
            case "s3" -> uploadToS3(file, documentId);
            case "local" -> uploadToLocal(file, documentId);
            default -> throw new IllegalStateException("Unknown storage type: " + storageType);
        };
    }

    /**
     * Get input stream for reading document.
     */
    public InputStream getInputStream(String bucket, String key) throws IOException {
        String storageType = appProperties.getStorage().getType();
        
        return switch (storageType) {
            case "s3" -> getInputStreamFromS3(bucket, key);
            case "local" -> getInputStreamFromLocal(key);
            default -> throw new IllegalStateException("Unknown storage type: " + storageType);
        };
    }

    /**
     * Generate pre-signed download URL (S3 only).
     */
    public String generateDownloadUrl(String bucket, String key) {
        if (!"s3".equals(appProperties.getStorage().getType())) {
            throw new UnsupportedOperationException("Pre-signed URLs only supported for S3 storage");
        }

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(
                appProperties.getStorage().getS3().getPresignedUrlExpiryMinutes()
            ))
            .getObjectRequest(getObjectRequest)
            .build();

        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
        
        log.debug("Generated pre-signed URL for s3://{}/{}", bucket, key);
        return presignedRequest.url().toString();
    }

    /**
     * Delete file from storage.
     */
    public void delete(String bucket, String key) {
        String storageType = appProperties.getStorage().getType();
        
        switch (storageType) {
            case "s3" -> deleteFromS3(bucket, key);
            case "local" -> deleteFromLocal(key);
            default -> throw new IllegalStateException("Unknown storage type: " + storageType);
        }
    }

    // ── S3 Implementation ──────────────────────────────────────

    private StorageMetadata uploadToS3(MultipartFile file, UUID documentId) throws IOException {
        String bucket = appProperties.getStorage().getS3().getBucket();
        String prefix = appProperties.getStorage().getS3().getPrefix();
        String key = prefix + documentId + "/" + file.getOriginalFilename();

        PutObjectRequest putRequest = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType(file.getContentType())
            .serverSideEncryption(ServerSideEncryption.AES256)
            .metadata(java.util.Map.of(
                "original-filename", file.getOriginalFilename(),
                "document-id", documentId.toString()
            ))
            .build();

        s3Client.putObject(putRequest, RequestBody.fromInputStream(
            file.getInputStream(),
            file.getSize()
        ));

        log.info("Uploaded file to S3: s3://{}/{}", bucket, key);

        return new StorageMetadata(bucket, key, file.getSize());
    }

    private InputStream getInputStreamFromS3(String bucket, String key) {
        GetObjectRequest getRequest = GetObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .build();

        return s3Client.getObject(getRequest);
    }

    private void deleteFromS3(String bucket, String key) {
        DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .build();

        s3Client.deleteObject(deleteRequest);
        log.info("Deleted file from S3: s3://{}/{}", bucket, key);
    }

    // ── Local Filesystem Implementation ────────────────────────

    private StorageMetadata uploadToLocal(MultipartFile file, UUID documentId) throws IOException {
        String uploadDir = appProperties.getStorage().getLocal().getUploadDir();
        Path dirPath = Paths.get(uploadDir, documentId.toString());
        
        // Create directory if it doesn't exist
        Files.createDirectories(dirPath);
        
        Path filePath = dirPath.resolve(file.getOriginalFilename());
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        log.info("Uploaded file to local storage: {}", filePath);

        return new StorageMetadata(
            "local",
            documentId + "/" + file.getOriginalFilename(),
            file.getSize()
        );
    }

    private InputStream getInputStreamFromLocal(String key) throws IOException {
        String uploadDir = appProperties.getStorage().getLocal().getUploadDir();
        Path filePath = Paths.get(uploadDir, key);
        
        if (!Files.exists(filePath)) {
            throw new IOException("File not found: " + filePath);
        }
        
        return Files.newInputStream(filePath);
    }

    private void deleteFromLocal(String key) {
        try {
            String uploadDir = appProperties.getStorage().getLocal().getUploadDir();
            Path filePath = Paths.get(uploadDir, key);
            Files.deleteIfExists(filePath);
            
            // Try to delete parent directory if empty
            Path parentDir = filePath.getParent();
            if (parentDir != null && Files.isDirectory(parentDir)) {
                try {
                    Files.delete(parentDir);
                } catch (Exception ignored) {
                    // Directory not empty or other issue, ignore
                }
            }
            
            log.info("Deleted file from local storage: {}", filePath);
        } catch (IOException e) {
            log.error("Failed to delete file: {}", key, e);
        }
    }

    /**
     * Storage metadata record.
     */
    public record StorageMetadata(
        String bucket,
        String key,
        long sizeBytes
    ) {}
}
