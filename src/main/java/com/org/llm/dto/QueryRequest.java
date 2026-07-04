package com.org.llm.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QueryRequest(
        @NotBlank(message = "question must not be blank")
        @Size(max = 2000, message = "question must be at most 2000 characters")
        String question,

        @Min(value = 1, message = "maxRows must be at least 1")
        @Max(value = 1000, message = "maxRows must be at most 1000")
        Integer maxRows) {
}
