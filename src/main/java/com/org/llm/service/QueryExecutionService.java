package com.org.llm.service;

import com.org.llm.config.Text2SqlProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

/**
 * Executes validated read-only SQL with a hard row cap and query timeout,
 * on a JdbcTemplate dedicated to LLM-generated statements.
 */
@Slf4j
@Service
public class QueryExecutionService {

    private final JdbcTemplate readOnlyTemplate;
    private final Text2SqlProperties properties;

    /**
     * @param dataSource the shared connection pool
     * @param properties text2sql tuning properties (row caps, query timeout)
     */
    public QueryExecutionService(DataSource dataSource, Text2SqlProperties properties) {
        this.properties = properties;
        this.readOnlyTemplate = new JdbcTemplate(dataSource);
        this.readOnlyTemplate.setQueryTimeout(properties.queryTimeoutSeconds());
    }

    /**
     * @param sql              a validated, single, read-only SQL statement
     * @param requestedMaxRows caller-requested row cap, clamped to {@code hardMaxRows}; {@code null} uses the default
     * @return the result rows, each as a column-name-to-value map
     */
    public List<Map<String, Object>> execute(String sql, Integer requestedMaxRows) {
        int maxRows = requestedMaxRows != null
                ? Math.min(requestedMaxRows, properties.hardMaxRows())
                : properties.defaultMaxRows();
        readOnlyTemplate.setMaxRows(maxRows);
        log.debug("Executing generated SQL (maxRows={}): {}", maxRows, sql);
        return readOnlyTemplate.queryForList(sql);
    }
}
