package com.eventnest.college;

import java.time.Instant;

/**
 * College directory payloads (ARCHITECTURE.md section 7.2).
 *
 * <p>Note what is missing from {@link CollegeSummary}: {@code verificationStatus}.
 * Only a verified college is listed, so the field would be a constant {@code true}
 * on every row - and on the rare read-only view where it is not, echoing it back
 * tells a caller nothing they did not already have to be told to see the row.
 * {@code verified} on the detail response is the useful version: it lets the
 * frontend show a badge.
 */
public final class CollegeDtos {

    private CollegeDtos() {
    }

    public record CollegeSummary(
            Long id,
            String name,
            String slug,
            String logoUrl,
            String websiteUrl,
            String city,
            String district,
            String state,
            boolean verified) {

        public static CollegeSummary from(College college) {
            return new CollegeSummary(
                    college.getId(),
                    college.getName(),
                    college.getSlug(),
                    college.getLogoUrl(),
                    college.getWebsiteUrl(),
                    college.getCity(),
                    college.getDistrict(),
                    college.getState(),
                    college.isVerified());
        }
    }

    /** Directory row plus the counts a directory is useless without. */
    public record CollegeListItem(
            Long id,
            String name,
            String slug,
            String logoUrl,
            String city,
            String state,
            boolean verified,
            long eventCount,
            long studentCount) {
    }

    /** Everything the public college page shows, plus its next few events. */
    public record CollegeDetail(
            Long id,
            String name,
            String slug,
            String logoUrl,
            String websiteUrl,
            String about,
            String city,
            String district,
            String state,
            String country,
            boolean verified,
            long eventCount,
            long studentCount,
            Instant createdAt) {
    }

    /**
     * The "add my own college" flow (section on registration forms). A student
     * types a name their college is missing from the dropdown; the backend
     * creates it as PENDING (never visible in the verified directory) and hands
     * back the id so registration can link to it. The same name on a later run
     * returns the existing row instead of duplicating - idempotency is what makes
     * "it'll be in the list next time" true.
     */
    public record CollegeCreateRequest(
            String name,
            String city,
            String state,
            String country) {
    }

    public record CollegeOption(
            Long id,
            String name,
            String slug,
            boolean verified) {
    }
}
