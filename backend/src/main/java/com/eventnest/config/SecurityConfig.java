package com.eventnest.config;

import com.eventnest.security.JwtAuthenticationFilter;
import com.eventnest.security.RestSecurityErrorHandlers;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * The security filter chain - authorisation layer 1 of three.
 *
 * <p>Read the {@code authorizeHttpRequests} block as a list of rules, from most
 * specific to least. Spring evaluates in order and the first match wins, which is
 * why {@code /admin/**} appears before {@code anyRequest()}.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestSecurityErrorHandlers securityErrorHandlers;
    private final AppProperties properties;

    /**
     * BCrypt with the default strength (10).
     *
     * <p>How BCrypt protects a password: it generates a random 16-byte salt,
     * mixes it into the password, and hashes the result 2^10 = 1024 times. Two
     * users with the password {@code password123} therefore have completely
     * different stored hashes, and a precomputed rainbow table is worthless.
     *
     * <p>Why not SHA-256 (or no hash)? Speed. Plain SHA-256 is so fast that an
     * attacker with a stolen database can try billions of guesses per second.
     * BCrypt's deliberate slowness is the defence. It also encodes the salt and
     * the cost in the stored string itself ({@code $2a$10$...}), so we can raise
     * the strength later and old hashes stay verifiable.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Exposed as a bean so {@code AuthService} can call
     * {@code authenticationManager.authenticate(...)} instead of hand-rolling a
     * password check - the framework's provider is what applies the encoder.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // D11: CSRF protection is OFF, and that is correct here - see the
                // comment below. Read it before copying this config to a project
                // that uses cookies.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // No server-side session: every request is authenticated from its
                // bearer token alone. This is what allows running N instances
                // behind a load balancer with no sticky sessions.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // --- public ---
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/**",
                                "/api/v1/colleges", "/api/v1/ai/chat").permitAll()
                        // Carve-out BEFORE the broad GET rule below: Spring Security
                        // matches in declaration order, so the narrower rule must come
                        // first or the student's registration status would leak to
                        // anonymous callers through the discovery matcher.
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/events/*/registration").hasRole("STUDENT")
                        // Writes against an event's roster or a student's own lists.
                        // Declared before anyRequest(): the verbs below are not GETs so
                        // the discovery matcher could never catch them, but keeping the
                        // whole student surface together is what makes the filter chain
                        // readable as a security model rather than a pile of rules.
                        .requestMatchers(HttpMethod.POST, "/api/v1/events/*/register",
                                "/api/v1/events/*/bookmark").hasRole("STUDENT")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/events/*/register",
                                "/api/v1/events/*/bookmark").hasRole("STUDENT")
                        // Carve-out first: the bell belongs to every signed-in role,
                        // not just students - an organizer's "someone registered"
                        // notification lives here too. Spring Security takes the first
                        // matcher that matches, so this must precede the STUDENT rule
                        // covering the rest of /me.
                        .requestMatchers("/api/v1/me/notifications/**").authenticated()
                        .requestMatchers("/api/v1/me/**").hasRole("STUDENT")
                        // Discovery is anonymous: a logged-out visitor must be able to
                        // browse events, categories and colleges, otherwise there is no
                        // funnel into registering. Only the verb is public - POSTing to
                        // the same paths (register, bookmark, report) still requires a
                        // token, and those matchers never match here.
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/events/**",
                                "/api/v1/categories/**",
                                "/api/v1/colleges/**",
                                "/api/v1/meta/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/me").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**",
                                "/swagger-ui.html", "/api/v1/files/**").permitAll()
                        // --- role-gated (layer 1) ---
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/organizer/**").hasRole("ORGANIZER")
                        // --- everything else ---
                        .anyRequest().authenticated())
                // Both handlers write our ApiError JSON. Without these, Spring
                // returns an empty 403 body for a bad token, because servlet
                // filters run outside @RestControllerAdvice.
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(securityErrorHandlers)
                        .accessDeniedHandler(securityErrorHandlers))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * CSRF, honestly explained (decision D11).
     *
     * <p>CSRF attacks work by exploiting the fact that browsers <em>automatically
     * attach cookies</em> to cross-site requests. Our access token travels in an
     * {@code Authorization} header, which JavaScript must set deliberately - a
     * cross-site form or image tag cannot set it, and without an accompanying
     * cookie the request arrives unauthenticated. So the classic vector is closed.
     *
     * <p><b>The caveat that matters:</b> the moment we move the refresh token into
     * an {@code httpOnly} cookie (a very reasonable hardening step against XSS),
     * the ambient-credential condition returns and CSRF protection must be turned
     * back on with a token. Disabling CSRF is only safe because of <em>how</em> the
     * token is transported - not because CSRF is obsolete.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // An allowlist, never "*": a wildcard would let any site on the internet
        // make authenticated requests on behalf of a logged-in user.
        config.setAllowedOrigins(properties.cors().allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setExposedHeaders(List.of("Location"));
        // False: we send the token in a header, not a cookie, so the browser does
        // not need to attach credentials cross-site.
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}