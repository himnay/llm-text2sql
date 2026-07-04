package com.org.llm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.text2sql")
public record Text2SqlProperties(
        String schemaOwner,
        int defaultMaxRows,
        int hardMaxRows,
        int queryTimeoutSeconds) {
}
