package com.eventnest.security;

import com.eventnest.common.exception.ApiError;
import com.eventnest.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.eventnest.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Writes 401/403 bodies as JSON, by hand.
 *
 * <p>This class is the answer to one of the most common real-world Spring
 * Security bugs: <b>servlet filters run outside {@code @RestControllerAdvice}</b>.
 * When {@code JwtAuthenticationFilter} rejects a token it is nowhere near a
 * controller, so no exception handler will ever run. The default behaviour is to
 * return an empty body with a 403 - which the frontend then reports as "the server
 * crashed". Registering these handlers produces the same {@link ApiError} JSON a
 * controller-thrown exception would.
 */
@Component
@RequiredArgsConstructor
public class RestSecurityErrorHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final AppProperties properties;

    /** 401: no token, bad token, or expired token. */
    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        write(response, request, 401, ErrorCode.UNAUTHORIZED,
                "Your session has expired or you are not signed in. Please sign in again.");
    }

    /**
     * 403: authenticated, but not allowed.
     *
     * <p>{@code Spring Boot} 3.x renamed the interface method to
     * {@code handle}, with {@code AccessDeniedHandlerImpl} adapting the old name -
     * implementing {@code handle} is what wires this into the chain.
     */
    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        write(response, request, 403, ErrorCode.FORBIDDEN,
                "You do not have permission to perform this action.");
    }

    private void write(
            HttpServletResponse response,
            HttpServletRequest request,
            int status,
            ErrorCode code,
            String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ApiError body = new ApiError(
                Instant.now(clock),
                status,
                code.name(),
                message,
                request.getRequestURI(),
                null,
                UUID.randomUUID().toString());

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}