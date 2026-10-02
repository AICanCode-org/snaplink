package org.aicancode.snaplink.insights;

import java.net.http.HttpClient;
import java.time.Duration;

/** Calls any OpenAI-compatible /chat/completions endpoint (Ollama, Gemini, Groq). */
public class LlmInsightGenerator implements InsightGenerator {

    static final String SYSTEM_PROMPT = "You are an analytics assistant for a URL shortener. Write at most two "
            + "short sentences in plain English, no markdown. Use only the numbers provided; never invent data.";

    private final String baseUrl;
    private final String model;
    private final String apiKey;
    private final Duration timeout;
    private final HttpClient http;

    public LlmInsightGenerator(String baseUrl, String model, String apiKey, int timeoutMs) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.apiKey = apiKey;
        this.timeout = Duration.ofMillis(timeoutMs);
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public String generate(InsightInput data) throws Exception {
        // TODO(phase-3 M5): POST baseUrl + "/chat/completions" with `http` (request timeout = timeout), a Bearer
        // header when apiKey is set, SYSTEM_PROMPT + the facts as JSON (model, temperature 0.2, max_tokens 120).
        // Throw on a non-2xx status or an empty answer; return choices[0].message.content trimmed.
        throw new UnsupportedOperationException("TODO(phase-3 M5): LlmInsightGenerator.generate");
    }
}
