package com.org.llm.exception;

public class PromptInjectionException extends RuntimeException {

    public PromptInjectionException(String message) {
        super(message);
    }
}
