package com.eventnest.admin;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Admin wire shapes (ARCHITECTURE.md §7.5).
 *
 * <p>Every admin list reuses the same {@link AdminFilters} record rather than
 * declaring its own query params, so the filter vocabulary (role, status,
 * q, page, size) is literally the same class everywhere. When the admin UI
 * grows a new filter it is added once and shows up consistently instead of
 * being implemented three times with three different spellings.
 */
public final class AdminDtos {

    private AdminDtos() {
    }

    public record AdminFilters(
            String role,
            String status,
            String categoryId,
            String q,
            int page,
            int size) {
    }

    /**
     * Platform totals plus the two queues an admin opens the app to work through.
     * Deliberately flat: a dashboard that nests counts two levels deep is a
     * dashboard nobody can read at a glance.
     */
    public record PlatformStats(
            long users,
            long students,
            long organizers,
            long admins,
            long colleges,
            long verifiedColleges,
            long events,
            long publishedEvents,
            long registrations,
            long confirmedRegistrations,
            long organizersPendingApproval,
            long collegesPendingVerification,
            long eventsPendingReview,
            long reportsOpen,
            List<DailyCount> trend) {
    }

    /** One day of the 30-day trend. Zero-filled so the chart has no gaps. */
    public record DailyCount(LocalDate day, long events, long registrations, long users) {
    }

    public record AdminUserRow(
            Long id,
            String email,
            String fullName,
            String role,
            String status,
            boolean emailVerified,
            Instant lastLoginAt,
            Instant createdAt) {
    }

    public record AdminOrganizerRow(
            Long id,
            Long userId,
            String fullName,
            String email,
            String designation,
            Long collegeId,
            String collegeName,
            String verificationStatus,
            String rejectionReason,
            Instant createdAt) {
    }

    public record AdminCollegeRow(
            Long id,
            String name,
            String slug,
            String city,
            String state,
            String verificationStatus,
            boolean moderationRequired,
            long eventCount,
            Instant createdAt) {
    }

    /** The moderation queue needs enough of the event to judge it at a glance. */
    public record AdminEventRow(
            Long id,
            String title,
            String slug,
            String status,
            boolean featured,
            boolean moderationRequired,
            Long organizerId,
            String organizerName,
            Long collegeId,
            String collegeName,
            String categoryName,
            Instant startsAt,
            Instant publishedAt,
            long registrationCount,
            Instant createdAt) {
    }

    // ------------------------------------------------------------- mutations --

    public record SetUserStatusRequest(String status) {
    }

    public record OrganizerApprovalRequest(String action, String reason) {
    }

    public record EventModerationRequest(String action, String reason) {
    }

    public record CollegeCreateRequest(
            String name,
            String city,
            String state,
            String country,
            String websiteUrl,
            String about,
            Boolean moderationRequired) {
    }

    public record CollegeUpdateRequest(
            String name,
            String city,
            String state,
            String country,
            String websiteUrl,
            String about,
            Boolean moderationRequired,
            String verificationStatus) {
    }

    public record CategoryRequest(
            String name,
            String slug,
            String icon,
            String description,
            Integer displayOrder,
            Boolean active) {
    }

    /** Every mutation answers with the row it changed, so the UI re-renders in place. */
    public record AdminUserResult(Long id, String status) {
    }

    public record AdminOrganizerResult(Long id, String verificationStatus) {
    }

    public record AdminEventResult(Long id, String status, boolean featured) {
    }

    public record AdminCollegeResult(Long id, String verificationStatus) {
    }
}
