package com.org.llm.exception;

public class SqlValidationException extends RuntimeException {

    public SqlValidationException(String message) {
        super(message);
    }
}
