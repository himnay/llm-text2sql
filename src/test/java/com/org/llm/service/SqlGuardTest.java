package com.org.llm.service;

import com.org.llm.exception.SqlValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqlGuardTest {

    private final SqlGuard guard = new SqlGuard();

    @Test
    @DisplayName("Plain SELECT statement passes through unchanged")
    void acceptsPlainSelect() {
        assertEquals("SELECT * FROM orders FETCH FIRST 10 ROWS ONLY",
                guard.validate("SELECT * FROM orders FETCH FIRST 10 ROWS ONLY"));
    }

    @Test
    @DisplayName("WITH clause (CTE) statement passes through unchanged")
    void acceptsWithClause() {
        String sql = "WITH t AS (SELECT customer_id FROM orders) SELECT * FROM t";
        assertEquals(sql, guard.validate(sql));
    }

    @Test
    @DisplayName("Block and line comments plus trailing semicolon are stripped")
    void stripsCommentsAndTrailingSemicolon() {
        String cleaned = guard.validate("SELECT 1 FROM dual /* block */ -- line\n;");
        assertEquals("SELECT 1 FROM dual", cleaned.strip().replaceAll("\\s+", " "));
    }

    @ParameterizedTest
    @DisplayName("Non-SELECT statements (DML, DDL, PL/SQL, multi-statement) are rejected")
    @ValueSource(strings = {
            "DELETE FROM orders",
            "INSERT INTO orders VALUES (1)",
            "UPDATE orders SET status = 'X'",
            "DROP TABLE orders",
            "TRUNCATE TABLE orders",
            "BEGIN NULL; END;",
            "GRANT SELECT ON orders TO PUBLIC",
            "SELECT * FROM orders; DELETE FROM orders"
    })
    void rejectsNonSelectStatements(String sql) {
        assertThrows(SqlValidationException.class, () -> guard.validate(sql));
    }

    @Test
    @DisplayName("Forbidden keyword smuggled inside an otherwise-valid SELECT is rejected")
    void rejectsForbiddenKeywordSmuggledInsideSelect() {
        assertThrows(SqlValidationException.class,
                () -> guard.validate("SELECT UTL_HTTP.REQUEST('http://evil') FROM dual"));
    }

    @Test
    @DisplayName("Blank SQL is rejected")
    void rejectsEmptySql() {
        assertThrows(SqlValidationException.class, () -> guard.validate("  "));
    }
}
