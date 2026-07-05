package com.org.llm.exception;

import com.org.llm.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * @param e       the bean-validation failure
     * @param request the failing request, used to populate the error path
     * @return a 400 response listing the failing fields
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e,
                                                          HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Invalid request");
        return error(HttpStatus.BAD_REQUEST, "Invalid request", message, request);
    }

    /**
     * @param e       the rejected-SQL failure
     * @param request the failing request, used to populate the error path
     * @return a 422 response describing why the generated SQL was rejected
     */
    @ExceptionHandler(SqlValidationException.class)
    public ResponseEntity<ErrorResponse> handleSqlValidation(SqlValidationException e,
                                                             HttpServletRequest request) {
        log.warn("Rejected generated SQL: {}", e.getMessage());
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "Generated SQL rejected", e.getMessage(), request);
    }

    /**
     * @param e       the prompt-injection detection failure
     * @param request the failing request, used to populate the error path
     * @return a 422 response describing which guard rejected the text
     */
    @ExceptionHandler(PromptInjectionException.class)
    public ResponseEntity<ErrorResponse> handlePromptInjection(PromptInjectionException e,
                                                               HttpServletRequest request) {
        log.warn("Rejected by PromptInjectionGuard: {}", e.getMessage());
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "Prompt injection detected", e.getMessage(), request);
    }

    /**
     * @param e       the unanswerable-question failure
     * @param request the failing request, used to populate the error path
     * @return a 422 response explaining why the schema cannot answer the question
     */
    @ExceptionHandler(UnanswerableQuestionException.class)
    public ResponseEntity<ErrorResponse> handleUnanswerable(UnanswerableQuestionException e,
                                                            HttpServletRequest request) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "Question not answerable from schema",
                e.getMessage(), request);
    }

    /**
     * @param e       the LLM/SQL-generation failure
     * @param request the failing request, used to populate the error path
     * @return a 502 response indicating the upstream model call failed
     */
    @ExceptionHandler(SqlGenerationException.class)
    public ResponseEntity<ErrorResponse> handleGeneration(SqlGenerationException e,
                                                          HttpServletRequest request) {
        log.error("SQL generation failed", e);
        return error(HttpStatus.BAD_GATEWAY, "SQL generation failed", e.getMessage(), request);
    }

    /**
     * @param e       the JDBC/query execution failure
     * @param request the failing request, used to populate the error path
     * @return a 422 response with the most specific underlying database error
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDataAccess(DataAccessException e,
                                                          HttpServletRequest request) {
        log.error("Query execution failed", e);
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "Query execution failed",
                e.getMostSpecificCause().getMessage(), request);
    }

    /**
     * @param e       any otherwise-unhandled failure
     * @param request the failing request, used to populate the error path
     * @return a 500 fallback response
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("Unexpected error", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error", e.getMessage(), request);
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String error, String message,
                                                HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(status.value(), error, message, request.getRequestURI()));
    }
}
