package com.eventnest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Application entry point.
 *
 * <p>Three annotations worth understanding:
 * <ul>
 *   <li>{@code @SpringBootApplication} = @Configuration + @EnableAutoConfiguration +
 *       @ComponentScan. Auto-configuration is what makes Spring Boot fast: it
 *       inspects the classpath and configures Postgres, JPA, Jackson, Security
 *       etc. without a single XML file.</li>
 *   <li>{@code @ConfigurationPropertiesScan} binds {@code app.*} from
 *       application.yml into typed {@link com.eventnest.config.AppProperties}
 *       records. Typed config beats reading strings from a map: the compiler and
 *       IDE can see the field names.</li>
 *   <li>{@code @EnableMethodSecurity} turns on {@code @PreAuthorize}, which is
 *       authorisation layer 2 of our three (see ARCHITECTURE.md section 5.4).</li>
 * </ul>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableMethodSecurity
@EnableScheduling
public class EventNestApplication {

    public static void main(String[] args) {
        SpringApplication.run(EventNestApplication.class, args);
    }
}
