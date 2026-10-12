package com.eventnest.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
    boolean enabled,
    String apiKey,
    String baseUrl,
    String model,
    @DefaultValue("0.2") double temperature,
    @DefaultValue("256") int maxTokens,
    @DefaultValue("30000") long timeoutMs
) {
}
