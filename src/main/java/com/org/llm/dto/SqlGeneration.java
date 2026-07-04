package com.org.llm.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record SqlGeneration(
        @JsonPropertyDescription("true if the question can be answered from the provided schema")
        boolean answerable,

        @JsonPropertyDescription("A single Oracle SELECT statement without a trailing semicolon; empty if not answerable")
        String sql,

        @JsonPropertyDescription("A short explanation of what the query does, or why the question is not answerable")
        String explanation) {
}
