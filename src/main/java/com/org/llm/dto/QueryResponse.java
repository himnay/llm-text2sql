package com.org.llm.dto;

import java.util.List;
import java.util.Map;

public record QueryResponse(
        String question,
        String sql,
        String explanation,
        List<String> columns,
        List<Map<String, Object>> rows,
        int rowCount,
        long executionTimeMs) {
}
