package com.org.llm.service;

import com.org.llm.config.Text2SqlProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Introspects the Oracle data dictionary and renders a compact schema
 * description used to ground the LLM prompt. The snapshot is cached in memory
 * and rebuilt on demand via {@link #refresh()}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchemaService {

    private static final String COLUMNS_SQL = """
            SELECT t.table_name,
                   tc.comments AS table_comment,
                   c.column_name,
                   c.data_type,
                   c.data_precision,
                   c.data_scale,
                   c.char_length,
                   c.nullable,
                   cc.comments AS column_comment
            FROM   all_tables t
            JOIN   all_tab_columns c
                   ON c.owner = t.owner AND c.table_name = t.table_name
            LEFT JOIN all_tab_comments tc
                   ON tc.owner = t.owner AND tc.table_name = t.table_name
            LEFT JOIN all_col_comments cc
                   ON cc.owner = c.owner AND cc.table_name = c.table_name
                  AND cc.column_name = c.column_name
            WHERE  t.owner = UPPER(?)
              AND  UPPER(t.table_name) <> 'FLYWAY_SCHEMA_HISTORY'
            ORDER  BY t.table_name, c.column_id
            """;

    private static final String PK_SQL = """
            SELECT acc.table_name, acc.column_name
            FROM   all_constraints ac
            JOIN   all_cons_columns acc
                   ON acc.owner = ac.owner AND acc.constraint_name = ac.constraint_name
            WHERE  ac.owner = UPPER(?) AND ac.constraint_type = 'P'
            """;

    private static final String FK_SQL = """
            SELECT acc.table_name,
                   acc.column_name,
                   rcc.table_name  AS ref_table,
                   rcc.column_name AS ref_column
            FROM   all_constraints ac
            JOIN   all_cons_columns acc
                   ON acc.owner = ac.owner AND acc.constraint_name = ac.constraint_name
            JOIN   all_cons_columns rcc
                   ON rcc.owner = ac.r_owner AND rcc.constraint_name = ac.r_constraint_name
                  AND rcc.position = acc.position
            WHERE  ac.owner = UPPER(?) AND ac.constraint_type = 'R'
            """;

    private final JdbcTemplate jdbcTemplate;
    private final Text2SqlProperties properties;

    private final AtomicReference<String> cachedSchema = new AtomicReference<>();

    /**
     * @return the cached schema snapshot, building it on first call
     */
    public String schemaDescription() {
        String schema = cachedSchema.get();
        if (schema == null) {
            schema = refresh();
        }
        return schema;
    }

    /**
     * Rebuilds the schema snapshot from the Oracle data dictionary and replaces the cached copy.
     *
     * @return the newly built schema snapshot
     */
    public String refresh() {
        String owner = properties.schemaOwner();
        log.info("Refreshing schema snapshot for owner {}", owner);

        List<Map<String, Object>> columns = jdbcTemplate.queryForList(COLUMNS_SQL, owner);
        if (columns.isEmpty()) {
            throw new IllegalStateException(
                    "No tables visible for schema owner '" + owner + "' — check TEXT2SQL_SCHEMA_OWNER and grants");
        }

        Map<String, String> primaryKeys = new LinkedHashMap<>();
        jdbcTemplate.queryForList(PK_SQL, owner).forEach(row -> primaryKeys.merge(
                (String) row.get("TABLE_NAME"), (String) row.get("COLUMN_NAME"), (a, b) -> a + ", " + b));

        Map<String, StringBuilder> foreignKeys = new LinkedHashMap<>();
        jdbcTemplate.queryForList(FK_SQL, owner).forEach(row -> foreignKeys
                .computeIfAbsent((String) row.get("TABLE_NAME"), k -> new StringBuilder())
                .append("  -- %s references %s(%s)%n".formatted(
                        row.get("COLUMN_NAME"), row.get("REF_TABLE"), row.get("REF_COLUMN"))));

        StringBuilder out = new StringBuilder();
        String currentTable = null;
        for (Map<String, Object> row : columns) {
            String table = (String) row.get("TABLE_NAME");
            if (!table.equals(currentTable)) {
                if (currentTable != null) {
                    closeTable(out, currentTable, primaryKeys, foreignKeys);
                }
                currentTable = table;
                out.append("TABLE ").append(table);
                Object tableComment = row.get("TABLE_COMMENT");
                if (tableComment != null) {
                    out.append(" -- ").append(tableComment);
                }
                out.append('\n');
            }
            out.append("  %s %s%s%s%n".formatted(
                    row.get("COLUMN_NAME"),
                    renderType(row),
                    "N".equals(row.get("NULLABLE")) ? " NOT NULL" : "",
                    row.get("COLUMN_COMMENT") != null ? " -- " + row.get("COLUMN_COMMENT") : ""));
        }
        closeTable(out, currentTable, primaryKeys, foreignKeys);

        String schema = out.toString();
        cachedSchema.set(schema);
        log.info("Schema snapshot refreshed: {} tables, {} chars",
                columns.stream().map(r -> r.get("TABLE_NAME")).distinct().count(), schema.length());
        return schema;
    }

    private void closeTable(StringBuilder out, String table,
                            Map<String, String> primaryKeys, Map<String, StringBuilder> foreignKeys) {
        String pk = primaryKeys.get(table);
        if (pk != null) {
            out.append("  -- primary key: ").append(pk).append('\n');
        }
        StringBuilder fk = foreignKeys.get(table);
        if (fk != null) {
            out.append(fk);
        }
        out.append('\n');
    }

    private String renderType(Map<String, Object> row) {
        String type = (String) row.get("DATA_TYPE");
        Object precision = row.get("DATA_PRECISION");
        Object scale = row.get("DATA_SCALE");
        Object charLength = row.get("CHAR_LENGTH");
        if ("NUMBER".equals(type) && precision != null) {
            return scale != null && ((Number) scale).intValue() > 0
                    ? "NUMBER(%s,%s)".formatted(precision, scale)
                    : "NUMBER(%s)".formatted(precision);
        }
        if ((type.startsWith("VARCHAR") || type.startsWith("NVARCHAR") || "CHAR".equals(type))
                && charLength != null && ((Number) charLength).intValue() > 0) {
            return "%s(%s)".formatted(type, charLength);
        }
        return type;
    }
}
