package com.org.llm.controller;

import com.org.llm.dto.QueryRequest;
import com.org.llm.dto.QueryResponse;
import com.org.llm.dto.SelectAiSetupRequest;
import com.org.llm.dto.SqlGeneration;
import com.org.llm.dto.SqlGenerationResponse;
import com.org.llm.service.SchemaService;
import com.org.llm.service.SelectAiService;
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
    private final SelectAiService selectAiService;

    /** Generate SQL and execute it against Oracle. */
    @PostMapping("/query")
    public QueryResponse query(@Valid @RequestBody QueryRequest request) {
        return textToSqlService.answer(request.question(), request.maxRows());
    }

    /** Send the question straight to Oracle's native Select AI ({@code DBMS_CLOUD_AI}) — the database generates and executes the SQL itself. */
    @PostMapping("/select-ai/query")
    public QueryResponse selectAiQuery(@Valid @RequestBody QueryRequest request) {
        return selectAiService.ask(request.question(), request.maxRows());
    }

    /**
     * One-time (idempotent) bootstrap of the DBMS_CLOUD_AI credential + profile that
     * {@code /select-ai/query} activates, using {@code SELECT_AI_PROVIDER_API_KEY}.
     * Requires the ACL + package grants from README's setup section to already exist.
     */
    @PostMapping("/select-ai/setup")
    public Map<String, String> selectAiSetup(@RequestBody(required = false) SelectAiSetupRequest request) {
        String provider = request != null ? request.provider() : null;
        String model = request != null ? request.model() : null;
        return Map.of("result", selectAiService.bootstrapProfile(provider, model));
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
