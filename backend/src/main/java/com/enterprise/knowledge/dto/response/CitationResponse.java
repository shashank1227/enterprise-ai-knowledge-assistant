package com.enterprise.knowledge.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CitationResponse {

    private int index;
    private UUID documentId;
    private String documentTitle;
    private UUID chunkId;
    private String excerpt;
    private Integer pageNumber;
    private String sectionTitle;
    private float relevanceScore;
}
