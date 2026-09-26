package com.org.llm.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectAiSetupRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("Typical provider and model names, or no overrides at all, are valid")
    void acceptsPlainNames() {
        assertTrue(validator.validate(new SelectAiSetupRequest("openai", "gpt-4.1-mini")).isEmpty());
        assertTrue(validator.validate(new SelectAiSetupRequest("oci", "meta.llama-3.3-70b-instruct")).isEmpty());
        assertTrue(validator.validate(new SelectAiSetupRequest(null, null)).isEmpty());
    }

    @Test
    @DisplayName("Values that could break out of the profile's JSON attributes are rejected")
    void rejectsJsonBreakout() {
        var request = new SelectAiSetupRequest("openai\", \"object_list\": [{\"owner\": \"SYS\"}], \"x\": \"",
                "gpt\"}");
        assertEquals(2, validator.validate(request).size());
    }
}
