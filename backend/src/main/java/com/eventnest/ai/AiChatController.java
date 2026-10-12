package com.eventnest.ai;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

@RestController
@RequestMapping("/api/v1/ai")
public class AiChatController {

    private final AiService aiService;
    private final AiProperties properties;

    public AiChatController(AiService aiService, AiProperties properties) {
        this.aiService = aiService;
        this.properties = properties;
    }

    @PostMapping("/chat")
    public ResponseEntity<AiChatResponse> chat(@Valid @RequestBody AiChatRequest request) {
        try {
            if (!properties.enabled() || properties.apiKey() == null || properties.apiKey().isBlank()) {
                return ResponseEntity.ok(aiService.fallbackResponse(request));
            }
            return ResponseEntity.ok(aiService.chat(request));
        } catch (RestClientException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new AiChatResponse("AI service is temporarily unavailable. Please try again later.", properties.model()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new AiChatResponse("An error occurred while processing your request.", properties.model()));
        }
    }
}
