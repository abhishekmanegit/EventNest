package com.eventnest.auth.dto;

import com.eventnest.user.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/**
 * Request and response shapes for authentication.
 *
 * <p>These are Java <strong>records</strong>, not entities, and that is the point
 * (decision D2). An entity would let Jackson serialise {@code passwordHash} if a
 * field were ever added carelessly; a record is an explicit contract of exactly
 * what crosses the wire.
 *
 * <p>They are also immutable, so a service cannot mutate the caller's input.
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    /**
     * Password policy, expressed once and reused by every password field so the
     * rule can never drift between the frontend hint and the server's answer.
     *
     * <p>Note the 72-character maximum: that is BCrypt's input limit. Anything
     * longer is silently truncated, which means two different passwords could
     * authenticate the same account. Rejecting them is more honest than pretending.
     */
    public static final String PASSWORD_REGEX =
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,72}$";

    public static final String PASSWORD_MESSAGE =
            "Password must be 8-72 characters and contain an uppercase letter, "
                    + "a lowercase letter, a digit and a symbol.";

    // ------------------------------------------------------- student ----

    public record StudentRegisterRequest(
            @NotBlank(message = "Full name is required")
            @Size(max = 120, message = "Name must be under 120 characters")
            String fullName,

            @NotBlank(message = "Email is required")
            @Email(message = "Must be a valid email address")
            @Size(max = 255)
            String email,

            @NotBlank(message = "Password is required")
            @Size(min = 8, max = 72, message = PASSWORD_MESSAGE)
            @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE)
            String password,

            Long collegeId,
            @Size(max = 80) String course,
            Integer graduationYear,
            @Size(max = 20) String phone,
            @Size(max = 80) String city,
            @Size(max = 80) String district,
            @Size(max = 80) String state,
            List<String> skills) {
    }

    // ---------------------------------------------------- organizer ----

    public record OrganizerRegisterRequest(
            @NotBlank(message = "Full name is required")
            @Size(max = 120)
            String fullName,

            @NotBlank(message = "Email is required")
            @Email(message = "Must be a valid email address")
            @Size(max = 255)
            String email,

            @NotBlank(message = "Password is required")
            @Size(min = 8, max = 72, message = PASSWORD_MESSAGE)
            @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE)
            String password,

            @NotNull(message = "Select your college")
            Long collegeId,

            @Size(max = 80) String designation,
            @Size(max = 120) String department,
            @Size(max = 20) String phone) {
    }

    // -------------------------------------------------------- common ----

    public record LoginRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Must be a valid email address")
            String email,

            @NotBlank(message = "Password is required")
            String password) {
    }

    public record RefreshRequest(
            @NotBlank(message = "Refresh token is required") String refreshToken) {
    }

    public record LogoutRequest(String refreshToken) {
    }

    // ------------------------------------------------------ response ----

    /**
     * @param expiresIn token lifetime in seconds, so the frontend can schedule its
     *                   proactive refresh instead of waiting for a 401
     */
    public record AuthResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            UserSummary user) {
    }

    public record UserSummary(
            Long id,
            String email,
            String fullName,
            String avatarUrl,
            UserRole role,
            String status,
            /** null for students; see OrganizerSummary for the approval state. */
            OrganizerSummary organizer) {
    }

    /**
     * Why the approval state is returned on every auth response: the frontend has
     * to be able to render "Awaiting admin approval" without a second round trip,
     * and it decides client-side routing from it.
     */
    public record OrganizerSummary(
            Long collegeId,
            String collegeName,
            String collegeSlug,
            String verificationStatus,
            boolean canPublish,
            String rejectionReason) {
    }

    public record UnreadCountResponse(int count) {
    }
}