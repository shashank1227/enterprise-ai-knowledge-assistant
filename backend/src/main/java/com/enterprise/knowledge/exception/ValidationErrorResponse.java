package com.enterprise.knowledge.exception;

import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ValidationErrorResponse extends ErrorResponse {
    private Map<String, String> fieldErrors;
}
