package com.enterprise.knowledge.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
public class ChatRequest {

    @NotBlank(message = "Message is required")
    @Size(min = 1, max = 2000, message = "Message must be between 1 and 2000 characters")
    private String message;

    private UUID conversationId;

    @Builder.Default
    private SearchMode searchMode = SearchMode.HYBRID;

    @Builder.Default
    private Integer topK = 5;

    private List<UUID> documentIds;

    public enum SearchMode {
        HYBRID, VECTOR, KEYWORD
    }
}
