package com.eventnest.ai;

import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class AiService {

    private static final String SYSTEM_PROMPT = "You are EventNest AI, a helpful assistant for EventNest - a campus event platform. " +
        "You help users with questions about events, colleges, registration, troubleshooting, and general usage. " +
        "Be concise, helpful, and accurate. If asked about something outside EventNest context, answer helpfully but stay grounded. " +
        "Keep responses under 500 words unless user explicitly asks for more detail.";

    private final RestClient aiRestClient;
    private final AiProperties properties;

    public AiService(RestClient aiRestClient, AiProperties properties) {
        this.aiRestClient = aiRestClient;
        this.properties = properties;
    }

    public AiChatResponse chat(AiChatRequest request) {
        String model = request.model() != null && !request.model().isBlank() ? request.model() : properties.model();
        double temperature = request.temperature() != null ? request.temperature() : properties.temperature();
        int maxTokens = request.maxTokens() != null ? request.maxTokens() : properties.maxTokens();

        Map<String, Object> body = Map.of(
            "model", model,
            "messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", request.message())
            ),
            "temperature", temperature,
            "max_tokens", maxTokens
        );

        Map response = aiRestClient.post()
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(Map.class);

        String content = extractContent(response);
        return new AiChatResponse(content != null ? content : "Sorry, I couldn't generate a response.", model);
    }

    @SuppressWarnings("unchecked")
    private String extractContent(Map response) {
        if (response == null) return null;
        try {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices == null || choices.isEmpty()) return null;
            Map<String, Object> choice = choices.get(0);
            Map<String, Object> message = (Map<String, Object>) choice.get("message");
            if (message != null) {
                Object content = message.get("content");
                return content != null ? content.toString() : null;
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    public AiChatResponse fallbackResponse(AiChatRequest request) {
        return new AiChatResponse(
            "I'm EventNest AI. The assistant is currently not connected to an LLM. You can still browse events, register, and use all existing features. " +
            "To enable full AI responses, add your Groq API key in backend configuration.",
            properties.model()
        );
    }
}
