package com.enterprise.knowledge.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackRequest {

    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be 1 or 5")
    @Max(value = 5, message = "Rating must be 1 or 5")
    private Short rating;

    private String comment;

    private FeedbackType feedbackType;

    public enum FeedbackType {
        HELPFUL, INACCURATE, INCOMPLETE, HARMFUL
    }
}
