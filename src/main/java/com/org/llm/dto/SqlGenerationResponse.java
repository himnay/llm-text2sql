package com.org.llm.dto;

public record SqlGenerationResponse(
        String question,
        String sql,
        String explanation) {
}
