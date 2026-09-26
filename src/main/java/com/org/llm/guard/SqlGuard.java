package com.org.llm.guard;

import com.org.llm.exception.SqlValidationException;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Defense-in-depth validation of LLM-generated SQL before execution.
 * The database connection is additionally read-only; this guard exists to
 * fail fast with a clear message instead of an ORA error.
 */
@Component
public class SqlGuard {

    private static final Pattern LINE_COMMENT = Pattern.compile("--.*?(\r?\n|$)");
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern WORD = Pattern.compile("[A-Z_]+");

    private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
            "INSERT", "UPDATE", "DELETE", "MERGE", "UPSERT",
            "CREATE", "ALTER", "DROP", "TRUNCATE", "RENAME", "PURGE",
            "GRANT", "REVOKE", "AUDIT", "COMMENT",
            "COMMIT", "ROLLBACK", "SAVEPOINT", "LOCK",
            "EXECUTE", "EXEC", "CALL", "BEGIN", "DECLARE");

    /**
     * Built-in packages stay callable from a plain SELECT: every UTL_* package reaches the
     * network or file system (UTL_HTTP, UTL_INADDR, UTL_FILE, ...), and most DBMS_* packages
     * run dynamic SQL or jobs, or have other side effects (DBMS_SQL, DBMS_SCHEDULER, DBMS_PIPE,
     * DBMS_LDAP, DBMS_CLOUD, ...). These two are read-only helpers a query may reasonably use.
     */
    private static final Set<String> ALLOWED_PACKAGES = Set.of("DBMS_LOB", "DBMS_RANDOM");

    /** URI types whose methods (e.g. HTTPURITYPE(url).GETCLOB()) fetch a URL from the database server. */
    private static final Set<String> URI_TYPES = Set.of("HTTPURITYPE", "DBURITYPE", "XDBURITYPE", "URIFACTORY");

    /**
     * @return the cleaned statement (comments stripped, trailing semicolon removed)
     */
    public String validate(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new SqlValidationException("Generated SQL is empty");
        }

        String cleaned = BLOCK_COMMENT.matcher(sql).replaceAll(" ");
        cleaned = LINE_COMMENT.matcher(cleaned).replaceAll(" ");
        cleaned = cleaned.strip();
        if (cleaned.endsWith(";")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).strip();
        }

        if (cleaned.contains(";")) {
            throw new SqlValidationException("Multiple SQL statements are not allowed");
        }

        String upper = cleaned.toUpperCase();
        if (!(upper.startsWith("SELECT") || upper.startsWith("WITH"))) {
            throw new SqlValidationException("Only SELECT statements are allowed");
        }

        var matcher = WORD.matcher(upper);
        while (matcher.find()) {
            String word = matcher.group();
            if (FORBIDDEN_KEYWORDS.contains(word)) {
                throw new SqlValidationException("Forbidden keyword in generated SQL: " + word);
            }
            if (isForbiddenPackage(word)) {
                throw new SqlValidationException("Forbidden package in generated SQL: " + word);
            }
        }
        return cleaned;
    }

    private static boolean isForbiddenPackage(String word) {
        return word.startsWith("UTL_")
                || word.startsWith("DBMS_") && !ALLOWED_PACKAGES.contains(word)
                || URI_TYPES.contains(word);
    }
}
