package com.eventnest.student;

import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Wire shapes for the student profile (ARCHITECTURE.md §7, {@code /me/profile}).
 */
public final class StudentProfileDtos {

    private StudentProfileDtos() {
    }

    public record StudentProfileResponse(
            Long userId,
            String fullName,
            String email,
            String avatarUrl,
            Long collegeId,
            String collegeName,
            String collegeSlug,
            String course,
            Integer graduationYear,
            String phone,
            String city,
            String district,
            String state,
            List<String> skills) {
    }

    /**
     * Full-replace semantics: the client submits the whole profile form and the
     * unchanged fields arrive present. Null = leave alone (so partial patches
     * work too); a non-null value replaces whatever was stored.
     */
    public record UpdateStudentProfileRequest(
            @Size(max = 120, message = "Name must be under 120 characters")
            String fullName,

            Long collegeId,

            @Size(max = 80, message = "Course must be under 80 characters")
            String course,

            Integer graduationYear,

            @Size(max = 20, message = "Phone must be under 20 characters")
            String phone,

            @Size(max = 80, message = "City must be under 80 characters")
            String city,

            @Size(max = 80, message = "District must be under 80 characters")
            String district,

            @Size(max = 80, message = "State must be under 80 characters")
            String state,

            @Size(max = 20, message = "You can add up to 20 skills")
            List<@Size(max = 40, message = "A skill must be under 40 characters") String> skills) {
    }
}