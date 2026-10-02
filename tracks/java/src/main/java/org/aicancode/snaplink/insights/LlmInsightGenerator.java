package org.aicancode.snaplink.insights;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/** Calls any OpenAI-compatible /chat/completions endpoint (Ollama, Gemini, Groq). */
public class LlmInsightGenerator implements InsightGenerator {

    static final String SYSTEM_PROMPT = "You are an analytics assistant for a URL shortener. Write at most two "
            + "short sentences in plain English, no markdown. Use only the numbers provided; never invent data.";

    private final String baseUrl;
    private final String model;
    private final String apiKey;
    private final Duration timeout;
    private final HttpClient http;
    private final JsonMapper json = JsonMapper.builder().build();

    public LlmInsightGenerator(String baseUrl, String model, String apiKey, int timeoutMs) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.apiKey = apiKey;
        this.timeout = Duration.ofMillis(timeoutMs);
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public String generate(InsightInput data) throws Exception {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("code", data.code());
        facts.put("allTimeClicks", data.allTimeClicks());
        facts.put("daily", data.daily().stream()
                .map(d -> Map.of("date", d.day().toString(), "clicks", d.clicks())).toList());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", 0.2);
        body.put("max_tokens", 120);
        body.put("messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", "Summarise these click stats:\n" + json.writeValueAsString(facts))));

        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        if (apiKey != null && !apiKey.isBlank()) {
            request.header("Authorization", "Bearer " + apiKey);
        }
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("LLM HTTP " + response.statusCode());
        }
        Map<?, ?> parsed = json.readValue(response.body(), Map.class);
        List<?> choices = (List<?>) parsed.get("choices");
        Map<?, ?> message = choices == null || choices.isEmpty() ? null
                : (Map<?, ?>) ((Map<?, ?>) choices.get(0)).get("message");
        String text = message == null ? null : (String) message.get("content");
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("empty LLM response");
        }
        return text.strip();
    }
}
