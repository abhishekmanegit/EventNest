package com.eventnest.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI at /swagger-ui.html.
 *
 * <p>Worth the one dependency: it is live documentation generated from the code,
 * so it cannot drift out of date the way a hand-written API doc always does. It
 * is also how you test the API by hand without Postman - and how you spot a
 * forgotten auth annotation on an endpoint.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI eventNestOpenApi(AppProperties properties) {
        String schemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title(properties.brand().name() + " API")
                        .version("v1")
                        .description("College event discovery and registration platform.")
                        .contact(new Contact().name("EventNest")))
                .components(new Components().addSecuritySchemes(schemeName,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                // Marks "Authorize" in the UI so you can paste a token from
                // /auth/login and call protected endpoints directly.
                .addSecurityItem(new SecurityRequirement().addList(schemeName));
    }
}
