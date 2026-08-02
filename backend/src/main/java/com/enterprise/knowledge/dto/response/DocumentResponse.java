package com.enterprise.knowledge.dto.response;

import com.enterprise.knowledge.domain.Document.DocumentStatus;
import com.enterprise.knowledge.domain.Document.FileType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentResponse {

    private UUID id;
    private String title;
    private String description;
    private String fileName;
    private FileType fileType;
    private Long fileSizeBytes;
    private DocumentStatus status;
    private String[] tags;
    private String category;
    private Integer chunkCount;
    private Integer pageCount;
    private UUID uploadedBy;
    private String uploadedByName;
    private Instant createdAt;
    private Instant indexedAt;
}
