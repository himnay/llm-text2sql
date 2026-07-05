package com.org.llm.guard;

import com.org.llm.exception.PromptInjectionException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Heuristic defense-in-depth scan for prompt-injection attempts, applied on
 * both sides of the LLM call: the caller's natural-language question before
 * it enters the system prompt ({@link #screenRequest(String)}), and the
 * model's generated explanation text before it leaves this service back to
 * the API caller ({@link #screenResponse(String)}) — the latter guards
 * against indirect injection, where the model's own output is crafted to
 * manipulate a downstream agent/consumer reading this API's response.
 * This is a heuristic denylist, not a guarantee — {@link SqlGuard} remains
 * the authoritative guardrail on what SQL is actually allowed to execute.
 */
@Component
public class PromptInjectionGuard {

    private static final int MAX_LENGTH = 4000;

    private static final List<Pattern> SUSPICIOUS_PATTERNS = List.of(
            Pattern.compile("ignore\\s+(all\\s+)?(previous|prior|above)\\s+instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("disregard\\s+(the\\s+)?(system|previous)\\s+prompt", Pattern.CASE_INSENSITIVE),
            Pattern.compile("you\\s+are\\s+now\\s+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("new\\s+instructions\\s*:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("reveal\\s+(your|the)\\s+(system\\s+)?prompt", Pattern.CASE_INSENSITIVE),
            Pattern.compile("print\\s+(your|the)\\s+(system\\s+)?instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("</?system>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bact\\s+as\\s+(an?\\s+)?(dan|jailbreak)\\b", Pattern.CASE_INSENSITIVE));

    /**
     * @param question the caller-supplied natural-language question, before it is interpolated into the system prompt
     */
    public void screenRequest(String question) {
        screen(question, "question");
    }

    /**
     * @param explanation the LLM-generated explanation text, before it is returned to the API caller
     */
    public void screenResponse(String explanation) {
        screen(explanation, "generated explanation");
    }

    private void screen(String text, String source) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (text.length() > MAX_LENGTH) {
            throw new PromptInjectionException("Rejected " + source + ": exceeds " + MAX_LENGTH + " characters");
        }
        for (Pattern pattern : SUSPICIOUS_PATTERNS) {
            if (pattern.matcher(text).find()) {
                throw new PromptInjectionException("Rejected " + source + ": matched a known prompt-injection pattern");
            }
        }
    }
}
