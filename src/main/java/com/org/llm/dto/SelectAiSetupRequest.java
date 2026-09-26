package com.org.llm.dto;

import jakarta.validation.constraints.Pattern;

/**
 * Optional overrides for {@code POST /api/v1/select-ai/setup}. Both values end up inside the
 * JSON attributes passed to {@code DBMS_CLOUD_AI.CREATE_PROFILE}, so they are restricted to
 * characters that cannot break out of a JSON string.
 */
public record SelectAiSetupRequest(
        @Pattern(regexp = "[A-Za-z0-9_-]{1,40}",
                message = "provider must be 1-40 letters, digits, '_' or '-'")
        String provider,

        @Pattern(regexp = "[A-Za-z0-9._:/-]{1,100}",
                message = "model must be 1-100 letters, digits, '.', '_', ':', '/' or '-'")
        String model) {
}
