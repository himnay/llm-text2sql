package com.org.llm.service;

import com.org.llm.config.Text2SqlProperties;
import com.org.llm.dto.QueryResponse;
import com.org.llm.dto.SqlGeneration;
import com.org.llm.exception.SqlGenerationException;
import com.org.llm.exception.UnanswerableQuestionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Orchestrates the Text-to-SQL pipeline:
 * schema snapshot -> LLM generation -> validation -> execution.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TextToSqlService {

    private final ChatClient chatClient;
    private final SchemaService schemaService;
    private final SqlGuard sqlGuard;
    private final QueryExecutionService queryExecutionService;
    private final Text2SqlProperties properties;

    @Value("classpath:/prompts/text2sql-system.st")
    private Resource systemPrompt;

    /**
     * @param question the natural-language question
     * @return a validated SQL generation result
     */
    public SqlGeneration generateSql(String question) {
        SqlGeneration generation;
        try {
            generation = chatClient.prompt()
                    .system(system -> system.text(systemPrompt)
                            .param("schema", schemaService.schemaDescription())
                            .param("defaultMaxRows", properties.defaultMaxRows()))
                    .user(question)
                    .call()
                    .entity(SqlGeneration.class);
        } catch (RuntimeException e) {
            throw new SqlGenerationException("SQL generation failed: " + e.getMessage(), e);
        }
        if (generation == null) {
            throw new SqlGenerationException("Model returned no parseable result");
        }
        if (!generation.answerable()) {
            throw new UnanswerableQuestionException(generation.explanation());
        }
        String cleanedSql = sqlGuard.validate(generation.sql());
        return new SqlGeneration(true, cleanedSql, generation.explanation());
    }

    /**
     * @param question the natural-language question
     * @param maxRows  caller-requested row cap; {@code null} uses the default
     * @return the generated SQL, its explanation, and the executed result set
     */
    public QueryResponse answer(String question, Integer maxRows) {
        SqlGeneration generation = generateSql(question);

        long start = System.currentTimeMillis();
        List<Map<String, Object>> rows = queryExecutionService.execute(generation.sql(), maxRows);
        long elapsed = System.currentTimeMillis() - start;

        List<String> columns = rows.isEmpty() ? List.of() : List.copyOf(rows.getFirst().keySet());
        return new QueryResponse(question, generation.sql(), generation.explanation(),
                columns, rows, rows.size(), elapsed);
    }
}
