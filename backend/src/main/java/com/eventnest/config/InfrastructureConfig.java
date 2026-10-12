package com.eventnest.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Small infrastructure beans that do not belong to any one feature module.
 */
@Configuration
@EnableJpaAuditing
public class InfrastructureConfig {

    /**
     * The single source of "now".
     *
     * <p>Injecting {@link Clock} everywhere instead of calling
     * {@code Instant.now()} means tests can freeze or advance time to test the
     * deadline logic ("registration closes tomorrow") without sleeping. That is
     * the difference between a test suite that takes four seconds and one that
     * takes four minutes - and the difference between a flaky test and a
     * trustworthy one.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
