package com.enterprise.knowledge.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    private UUID messageId;
    private UUID conversationId;
    private String answer;
    private List<CitationResponse> citations;
    private Integer tokensUsed;
    private Integer latencyMs;
    private String model;
    private Float confidenceScore;
}
