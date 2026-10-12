package com.eventnest.organizer;

import jakarta.validation.constraints.Size;

/**
 * Wire shapes for the organizer profile and college-logo surface
 * (ARCHITECTURE.md §7.4).
 */
public final class OrganizerProfileDtos {

    private OrganizerProfileDtos() {
    }

    /**
     * The full "who am I on this platform" read: profile fields plus the college
     * they publish for, in one round trip, so the profile page renders without a
     * second call to {@code /colleges/{id}}.
     */
    public record OrganizerProfileResponse(
            Long userId,
            String fullName,
            String email,
            String avatarUrl,
            String designation,
            String department,
            String phone,
            Long collegeId,
            String collegeName,
            String collegeSlug,
            String collegeLogoUrl,
            String collegeCity,
            String collegeState,
            String verificationStatus,
            boolean canPublish,
            String rejectionReason) {
    }

    /**
     * All four fields are optional because the client submits the whole form and
     * the unchanged fields arrive present; null simply means "leave alone" at the
     * protocol level. Each still has a size guard so a runaway field cannot soak
     * up bandwidth (or insert a multi-KB string into a sized column).
     */
    public record UpdateOrganizerProfileRequest(
            @Size(max = 120, message = "Name must be under 120 characters")
            String fullName,

            @Size(max = 80, message = "Designation must be under 80 characters")
            String designation,

            @Size(max = 120, message = "Department must be under 120 characters")
            String department,

            @Size(max = 20, message = "Phone must be under 20 characters")
            String phone) {
    }

    public record LogoUploadResponse(String logoUrl) {
    }
}