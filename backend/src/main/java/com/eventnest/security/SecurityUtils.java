package com.eventnest.security;

import com.eventnest.common.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The one place to ask "who is calling?" (authorisation layer 3's starting point).
 *
 * <p>Every {@code create}/{@code update}/{@code delete} in the service layer
 * begins with {@code SecurityUtils.currentUserId()} and compares it against the
 * row's owner. Controllers never do this comparison themselves: ownership is a
 * business rule, so it belongs next to the transaction, not in the transport
 * layer where it can be forgotten.
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * @throws UnauthorizedException if the request is not authenticated. Reaching
     *         here means an endpoint that requires a principal was reached without
     *         one - a missing method-level guard, not a client error.
     */
    public static Long currentUserId() {
        return principal().id();
    }

    public static EventNestUserPrincipal principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof EventNestUserPrincipal p)) {
            throw new UnauthorizedException(
                    "You need to be signed in to perform this action.");
        }
        return p;
    }

    public static String currentUserRole() {
        return principal().role();
    }

    /**
     * Null-safe variant for endpoints that serve both anonymous and signed-in
     * callers (event detail is the main one: a logged-out visitor may read a
     * published event, and only the ownership/visibility check needs a role).
     * Using {@link #currentUserRole()} there would throw on every anonymous
     * request.
     */
    public static String currentUserRoleOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof EventNestUserPrincipal p) {
            return p.role();
        }
        return null;
    }

    public static boolean hasRole(String role) {
        return role.equals(currentUserRole());
    }

    /** Defensive null-safe accessor used by endpoints that work logged-in or logged-out. */
    public static Long currentUserIdOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof EventNestUserPrincipal p) {
            return p.id();
        }
        return null;
    }
}