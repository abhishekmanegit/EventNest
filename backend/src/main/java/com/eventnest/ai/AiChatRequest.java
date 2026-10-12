package com.eventnest.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiChatRequest(
    @NotBlank(message = "Message is required")
    @Size(max = 2000, message = "Message cannot exceed 2000 characters")
    String message,

    @JsonProperty("model")
    @Size(max = 100)
    String model,

    @Min(0) @Max(2)
    Double temperature,

    @Min(1) @Max(4096)
    Integer maxTokens
) {
}
