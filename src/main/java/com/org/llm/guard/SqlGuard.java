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
            "EXECUTE", "EXEC", "CALL", "BEGIN", "DECLARE",
            "DBMS_SQL", "DBMS_SCHEDULER", "UTL_FILE", "UTL_HTTP", "UTL_TCP", "UTL_SMTP");

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
        }
        return cleaned;
    }
}
