package com.eventnest.security;

import com.eventnest.user.UserRepository;
import com.eventnest.user.UserStatus;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Runs once per request and turns a valid {@code Authorization: Bearer} header
 * into a populated {@code SecurityContext}.
 *
 * <p>Three things that are easy to get wrong and that this implementation handles
 * deliberately:
 *
 * <ol>
 *   <li><b>An absent token is not an error.</b> A logged-out visitor browsing
 *       {@code /api/v1/events} sends no header. We simply pass the request
 *       through unauthenticated and let the authorisation rules decide what is
 *       permitted - otherwise every public endpoint would 401.</li>
 *
 *   <li><b>An invalid token on a public endpoint should not break it.</b> We do
 *       not throw; we leave the context empty. If the request only needs public
 *       data it succeeds, and if it needs auth the authorisation rules reject it
 *       with the JSON 401 from {@link RestSecurityErrorHandlers}. That gives a
 *       better experience than failing a public request because a stale token
 *       was in localStorage.</li>
 *
 *   <li><b>Read the header defensively.</b> A malformed header must not throw out
 *       of the filter chain, or the response becomes an opaque 500.</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final RestSecurityErrorHandlers errorHandlers;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String token = extractToken(request);

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            Claims claims = jwtService.parseAccessToken(token);
            if (claims != null) {
                authenticate(claims, request);
            }
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(Claims claims, HttpServletRequest request) {
        try {
            Long userId = Long.valueOf(claims.getSubject());
            String role = claims.get(JwtService.CLAIM_ROLE, String.class);

            // A stateless token cannot be revoked by deleting it - it stays valid
            // until it expires. So "suspend this account" would be a lie for up to
            // accessTokenTtl unless something checks the database, which is
            // exactly what makes suspension a real control rather than a
            // cosmetic flag. One indexed primary-key lookup per *authenticated*
            // request is the price; anonymous requests never reach this line.
            //
            // If that read ever shows up in a profile, the fix is a short-TTL
            // cache on status (a 30-second stale window is still a 30-second
            // lockout), not dropping the check - a revocation mechanism that
            // takes 15 minutes to take effect is not a revocation mechanism.
            if (isLockedOut(userId)) {
                SecurityContextHolder.clearContext();
                return;
            }

            // Everything else comes from the CLAIMS - email is null here, is never
            // needed on this path, and any endpoint that needs profile data reads
            // it via /auth/me or its own repository query.
            EventNestUserPrincipal principal = new EventNestUserPrincipal(
                    userId,
                    null,
                    claims.get(JwtService.CLAIM_NAME, String.class),
                    role,
                    true,
                    java.util.List.of(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role)));

            var authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, principal.getAuthorities());
            // IP + user agent onto the authentication details, for the audit trail
            // on refresh_tokens.
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (NumberFormatException | IllegalStateException ex) {
            // Malformed subject. Ignore and continue unauthenticated.
            SecurityContextHolder.clearContext();
        }
    }

    /** @return true when the account no longer exists or is no longer active. */
    private boolean isLockedOut(Long userId) {
        return userRepository.findStatusById(userId)
                .map(status -> status != UserStatus.ACTIVE)
                .orElse(true);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            return null;
        }
        String token = header.substring(PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}