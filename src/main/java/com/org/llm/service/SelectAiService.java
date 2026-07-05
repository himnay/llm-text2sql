package com.org.llm.service;

import com.org.llm.config.Text2SqlProperties;
import com.org.llm.dto.QueryResponse;
import com.org.llm.exception.SqlValidationException;
import com.org.llm.guard.PromptInjectionGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.stereotype.Service;

import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Sends the natural-language question straight to Oracle's native Select AI
 * ({@code DBMS_CLOUD_AI}) instead of this app's own Spring AI pipeline: the
 * database itself calls the configured LLM provider, generates SQL, executes
 * it, and returns rows. See README §3/§5 for how this differs from the
 * app-level {@code /api/v1/query} pipeline.
 * <p>
 * {@code SET_PROFILE} is session-scoped, so it must run on the exact same
 * physical connection as the {@code SELECT AI} call that follows it — with a
 * pooled DataSource, two separate {@code JdbcTemplate} calls could each
 * borrow a different connection. Both statements are therefore issued inside
 * one {@link JdbcTemplate#execute(org.springframework.jdbc.core.ConnectionCallback)}
 * callback, which holds a single borrowed connection for its whole body.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SelectAiService {

    private final JdbcTemplate jdbcTemplate;
    private final Text2SqlProperties properties;
    private final PromptInjectionGuard promptInjectionGuard;

    /**
     * @param question         the natural-language question, passed to Oracle verbatim as {@code SELECT AI runsql <question>}
     * @param requestedMaxRows caller-requested row cap, clamped to {@code hardMaxRows}; {@code null} uses the default
     * @return the executed SQL Oracle generated internally, plus the result rows
     */
    public QueryResponse ask(String question, Integer requestedMaxRows) {
        // The question is concatenated into the SQL text (Select AI has no bind-parameter
        // form for the natural-language clause) — reject the one character that could
        // smuggle a second statement past the single executeQuery() call.
        if (question.indexOf(';') >= 0) {
            throw new SqlValidationException("Question must not contain ';'");
        }
        promptInjectionGuard.screenRequest(question);
        int maxRows = requestedMaxRows != null
                ? Math.min(requestedMaxRows, properties.hardMaxRows())
                : properties.defaultMaxRows();
        String sql = "SELECT AI runsql " + question;

        long start = System.currentTimeMillis();
        List<Map<String, Object>> rows = jdbcTemplate.execute((java.sql.Connection con) -> {
            try (CallableStatement setProfile = con.prepareCall("{call DBMS_CLOUD_AI.SET_PROFILE(?)}")) {
                setProfile.setString(1, properties.selectAiProfile());
                setProfile.execute();
            }
            try (Statement stmt = con.createStatement()) {
                stmt.setMaxRows(maxRows);
                stmt.setQueryTimeout(properties.queryTimeoutSeconds());
                log.debug("Select AI runsql (profile={}, maxRows={}): {}", properties.selectAiProfile(), maxRows, question);
                try (ResultSet rs = stmt.executeQuery(sql)) {
                    return extractRows(rs);
                }
            }
        });
        long elapsed = System.currentTimeMillis() - start;

        List<String> columns = rows.isEmpty() ? List.of() : List.copyOf(rows.getFirst().keySet());
        String explanation = "Generated and executed natively by Oracle Select AI (profile: "
                + properties.selectAiProfile() + ") — no app-level SQL generation or SqlGuard involved.";
        return new QueryResponse(question, sql, explanation, columns, rows, rows.size(), elapsed);
    }

    /**
     * One-time (idempotent) bootstrap of the {@code DBMS_CLOUD_AI} credential and profile that
     * {@link #ask} activates before every query. Requires that a database ADMIN has already run
     * the ACL + {@code GRANT EXECUTE ON DBMS_CLOUD}/{@code DBMS_CLOUD_AI} step from README's
     * "Native Oracle Select AI passthrough" section — this method only does the part that can run
     * as the regular app schema user, using the provider API key from {@code SELECT_AI_PROVIDER_API_KEY}.
     *
     * @param providerOverride null to use the configured {@code select-ai-provider} default
     * @param modelOverride    null to use the configured {@code select-ai-model} default
     * @return a human-readable summary of what was (re)created
     */
    public String bootstrapProfile(String providerOverride, String modelOverride) {
        String apiKey = properties.selectAiProviderApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "SELECT_AI_PROVIDER_API_KEY is not set — export it before calling this endpoint");
        }
        String provider = providerOverride != null ? providerOverride : properties.selectAiProvider();
        String model = modelOverride != null ? modelOverride : properties.selectAiModel();
        String credentialName = properties.selectAiProfile() + "_CRED";

        List<Map<String, Object>> tables = jdbcTemplate.queryForList(
                "SELECT owner, table_name FROM all_tables WHERE owner = UPPER(?) AND table_name <> 'FLYWAY_SCHEMA_HISTORY'",
                properties.schemaOwner());
        if (tables.isEmpty()) {
            throw new IllegalStateException(
                    "No tables visible for schema owner '" + properties.schemaOwner() + "' — nothing to scope the AI profile to");
        }
        String objectList = tables.stream()
                .map(t -> "{\"owner\": \"%s\", \"name\": \"%s\"}".formatted(t.get("OWNER"), t.get("TABLE_NAME")))
                .collect(Collectors.joining(",", "[", "]"));
        String attributes = """
                {"provider": "%s", "model": "%s", "credential_name": "%s", "object_list": %s, "conversation": "true"}
                """.formatted(provider, model, credentialName, objectList);

        jdbcTemplate.execute((java.sql.Connection con) -> {
            // DROP first so re-running this endpoint (new key, new provider) is idempotent —
            // DBMS_CLOUD/DBMS_CLOUD_AI don't offer CREATE_OR_REPLACE, and both DROP calls throw if
            // the object doesn't exist yet, which is expected on the very first run.
            silentDrop(con, "BEGIN DBMS_CLOUD.DROP_CREDENTIAL(?); END;", credentialName);
            silentDrop(con, "BEGIN DBMS_CLOUD_AI.DROP_PROFILE(?, force => TRUE); END;", properties.selectAiProfile());

            try (CallableStatement createCred = con.prepareCall(
                    "{call DBMS_CLOUD.CREATE_CREDENTIAL(credential_name => ?, username => ?, password => ?)}")) {
                createCred.setString(1, credentialName);
                createCred.setString(2, provider.toUpperCase());
                createCred.setString(3, apiKey);
                createCred.execute();
            }
            try (CallableStatement createProfile = con.prepareCall("{call DBMS_CLOUD_AI.CREATE_PROFILE(?, ?)}")) {
                createProfile.setString(1, properties.selectAiProfile());
                createProfile.setString(2, attributes);
                createProfile.execute();
            }
            return null;
        });

        log.info("Select AI profile '{}' (re)created: provider={}, model={}, {} tables in scope",
                properties.selectAiProfile(), provider, model, tables.size());
        return "Profile '%s' ready — provider=%s, model=%s, %d table(s) in scope. Credential '%s' stored in the database."
                .formatted(properties.selectAiProfile(), provider, model, tables.size(), credentialName);
    }

    private void silentDrop(java.sql.Connection con, String plsql, String name) throws SQLException {
        try (CallableStatement drop = con.prepareCall(plsql)) {
            drop.setString(1, name);
            drop.execute();
        } catch (SQLException e) {
            log.debug("Drop before recreate ({}): {}", name, e.getMessage());
        }
    }

    private List<Map<String, Object>> extractRows(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();
        List<Map<String, Object>> rows = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                row.put(JdbcUtils.lookupColumnName(meta, i), JdbcUtils.getResultSetValue(rs, i));
            }
            rows.add(row);
        }
        return rows;
    }
}
