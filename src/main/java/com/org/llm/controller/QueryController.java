package com.org.llm.controller;

import com.org.llm.dto.QueryRequest;
import com.org.llm.dto.QueryResponse;
import com.org.llm.dto.SqlGeneration;
import com.org.llm.dto.SqlGenerationResponse;
import com.org.llm.service.SchemaService;
import com.org.llm.service.TextToSqlService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class QueryController {

    private final TextToSqlService textToSqlService;
    private final SchemaService schemaService;

    /** Generate SQL and execute it against Oracle. */
    @PostMapping("/query")
    public QueryResponse query(@Valid @RequestBody QueryRequest request) {
        return textToSqlService.answer(request.question(), request.maxRows());
    }

    /** Generate SQL only — dry run, nothing is executed. */
    @PostMapping("/sql/generate")
    public SqlGenerationResponse generate(@Valid @RequestBody QueryRequest request) {
        SqlGeneration generation = textToSqlService.generateSql(request.question());
        return new SqlGenerationResponse(request.question(), generation.sql(), generation.explanation());
    }

    /** The schema snapshot currently grounding the prompt. */
    @GetMapping("/schema")
    public Map<String, String> schema() {
        return Map.of("schema", schemaService.schemaDescription());
    }

    /** Rebuild the schema snapshot after DDL changes. */
    @PostMapping("/schema/refresh")
    public Map<String, String> refreshSchema() {
        return Map.of("schema", schemaService.refresh());
    }
}
