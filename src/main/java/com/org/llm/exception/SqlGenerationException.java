package com.org.llm.exception;

public class SqlGenerationException extends RuntimeException {

    public SqlGenerationException(String message) {
        super(message);
    }

    public SqlGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
