package com.eventnest.event.dto;

import com.eventnest.event.EventMode;
import com.eventnest.event.EventRegistrationMode;
import com.eventnest.event.EventStatus;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The event API contract, as records.
 *
 * <p>Same reasoning as {@code AuthDtos}: records give us an immutable,
 * equals-implementing value type with no boilerplate, and nesting them in one
 * file keeps the whole contract visible in a single place - which is the thing
 * you actually want to read when reviewing a change to the API.
 *
 * <p>Note what is <em>not</em> in the requests: {@code organizerId},
 * {@code collegeId} and {@code moderationRequired}. Those are derived server-side
 * from the authenticated organizer (colleges are shared reference data, and the
 * college a choice is made against must be the organizer's own college), which
 * means a client cannot publish on behalf of someone else by editing JSON.
 */
public final class EventDtos {

    private EventDtos() {
    }

    // ---------------------------------------------------------------- reads --

    /** Compact form used by every list, rail and search result. */
    public record EventSummary(
            Long id,
            String slug,
            String title,
            String summary,
            String posterUrl,
            EventMode mode,
            String venue,
            String city,
            String state,
            Instant startsAt,
            Instant endsAt,
            Instant registrationDeadline,
            EventStatus status,
            int registrationCount,
            /**
             * Accumulated detail-page views. Surfaced because it is what the
             * {@code POPULAR} sort and the "Trending" rail actually rank on -
             * a sort criterion the API never shows would be unverifiable and
             * therefore untrustworthy.
             */
            int viewCount,
            Integer maxParticipants,
            boolean registrationOpen,
            CategoryRef category,
            CollegeRef college,
            String organizerName,
            String prizeTitle,
            BigDecimal prizeAmount,
            String prizeCurrency,
            List<String> tags,
            /** Only present when a token was supplied; {@code false} when anonymous. */
            boolean bookmarked,
            boolean registered) {

        public record CategoryRef(Long id, String name, String slug, String icon, String colorHex) {
        }

        public record CollegeRef(Long id, String name, String slug, String logoUrl, String city, String state) {
        }

        /**
         * Two-phase construction: {@link com.eventnest.event.EventMapper} produces
         * the mechanical field mapping, then the caller stamps on what only it can
         * know - whether <em>this</em> viewer has saved or registered for the event.
         *
         * <p>Doing it this way keeps those two flags out of the mapper, where they
         * would otherwise need a viewer-specific context object threaded through
         * every mapping method, and makes the viewer-dependent part of the response
         * impossible to miss when reading the service.
         */
        public EventSummary withViewerFlags(boolean bookmarked, boolean registered) {
            if (this.bookmarked == bookmarked && this.registered == registered) {
                return this;
            }
            return new EventSummary(id, slug, title, summary, posterUrl, mode, venue, city, state,
                    startsAt, endsAt, registrationDeadline, status, registrationCount, viewCount,
                    maxParticipants, registrationOpen, category, college, organizerName, prizeTitle,
                    prizeAmount, prizeCurrency, tags, bookmarked, registered);
        }
    }

    /** Everything on the detail page, including fields too heavy for a list. */
    public record EventDetail(
            EventSummary summary,
            String description,
            String addressLine,
            String district,
            String country,
            BigDecimal latitude,
            BigDecimal longitude,
            EventRegistrationMode registrationMode,
            String externalRegistrationUrl,
            Integer teamSizeMin,
            Integer teamSizeMax,
            String prizeDescription,
            String eligibility,
            String rules,
            JsonNode schedule,
            String coordinatorName,
            String coordinatorEmail,
            String coordinatorPhone,
            String bannerUrl,
            boolean moderationRequired,
            Instant publishedAt,
            /**
             * Both {@code null} unless the event was cancelled. The reason is on
             * the detail rather than the summary because it is a full sentence a
             * list row has no room for - a list simply marks the row
             * {@code CANCELLED}.
             */
            Instant cancelledAt,
            String cancellationReason,
            Instant createdAt,
            Instant updatedAt,
            /** {@code null} when anonymous or when the viewer never registered. */
            MyRegistration myRegistration) {
    }

    /** What the viewer needs to render "You are going" / "Registration closed". */
    public record MyRegistration(
            Long registrationId,
            String registrationNumber,
            String status,
            Instant registeredAt) {
    }

    /**
     * The home page, served by <b>one</b> request.
     *
     * <p>The alternative - six separate calls, one per rail - would make the first
     * paint six round-trips and let the rails disagree with each other (a page
     * that shows an event under both "Closing Soon" and "Trending" because two
     * queries ran a second apart). One endpoint is also the honest shape: these
     * are all views of the same table at the same instant.
     *
     * <p>The tradeoff is that a caller who wants only one rail still pays for all
     * six. That is acceptable because each rail is capped (six rows) and backed by
     * its own index; if a future caller needed an unbounded variant it would get a
     * separate endpoint, not a query parameter here.
     *
     * @param byCategory keyed by category <b>slug</b>, ordered by the category's
     *        {@code display_order} so the grid never reshuffles between loads
     */
    public record HomeResponse(
            List<EventSummary> recommended,
            List<EventSummary> nearYou,
            List<EventSummary> trending,
            java.util.Map<String, List<EventSummary>> byCategory,
            List<EventSummary> closingSoon,
            List<EventSummary> newlyAdded) {
    }

    // --------------------------------------------------------------- writes --

    /**
     * Shared by create and update. All fields are re-validated on every save, so
     * a partial update that would violate a rule (an end before its start) fails
     * with a 400 rather than being silently accepted.
     */
    public record EventWriteRequest(
            @NotBlank(message = "Title is required")
            @Size(max = 200, message = "Title must be 200 characters or fewer")
            String title,

            @Size(max = 300, message = "Summary must be 300 characters or fewer")
            String summary,

            @NotBlank(message = "Description is required")
            String description,

            @NotNull(message = "Category is required")
            Long categoryId,

            @Size(max = 400) String posterUrl,
            @Size(max = 400) String bannerUrl,

            EventMode mode,
            @Size(max = 255) String venue,
            @Size(max = 300) String addressLine,
            @Size(max = 80) String city,
            @Size(max = 80) String district,
            @Size(max = 80) String state,
            @Size(max = 80) String country,

            @NotNull(message = "Start time is required") Instant startsAt,
            @NotNull(message = "End time is required") Instant endsAt,
            @NotNull(message = "Registration deadline is required") Instant registrationDeadline,

            EventRegistrationMode registrationMode,
            @Size(max = 500) String externalRegistrationUrl,

            @Positive(message = "Capacity must be greater than zero") Integer maxParticipants,
            Integer teamSizeMin,
            Integer teamSizeMax,

            @Size(max = 150) String prizeTitle,
            BigDecimal prizeAmount,
            @Size(max = 3) String prizeCurrency,
            String prizeDescription,
            String eligibility,
            String rules,
            JsonNode schedule,
            List<@Size(max = 40) String> tags,

            @Size(max = 120) String coordinatorName,
            String coordinatorEmail,
            @Size(max = 20) String coordinatorPhone) {

        public EventWriteRequest {
            // Records run their compact constructor after the field assignments but
            // before anything else, so normalising here means no caller can bypass it.
            mode = mode == null ? EventMode.OFFLINE : mode;
            registrationMode = registrationMode == null ? EventRegistrationMode.INTERNAL : registrationMode;
            prizeCurrency = prizeCurrency == null || prizeCurrency.isBlank() ? "INR" : prizeCurrency.trim();
            if (country == null || country.isBlank()) {
                country = "India";
            }
        }
    }

    /**
     * {@code PUBLISH} is a no-op if already published and {@code SUBMIT} is
     * rejected for colleges without a moderation gate - both are legal HTTP
     * requests that simply do nothing useful, which is why they are modelled as
     * an action rather than as a raw status patch: letting a client write any
     * status it likes would make the state machine decorative.
     */
    public record StatusActionRequest(
            @NotNull(message = "Action is required") Action action,
            @Size(max = 300) String reason) {

        public enum Action {
            SUBMIT, PUBLISH, CANCEL, COMPLETE
        }
    }

    /**
     * Discovery query for {@code GET /events}. Every field is optional; the
     * service applies only the ones that are present, which is why one method can
     * serve the browse page, the search box and the filter panel.
     */
    public record EventQuery(
            String q,
            String category,
            Long collegeId,
            String city,
            String district,
            String state,
            EventMode mode,
            Instant from,
            Instant to,
            /**
             * All three are boxed rather than {@code boolean} for the same reason as
             * {@code page} below: with a primitive, an <em>absent</em> query parameter
             * arrives as {@code null} and Spring's binder fails the conversion - the
             * endpoint would 400 on its own default URL. Boxed, absent means "not
             * specified" and the service applies the default.
             */
            Boolean includeClosed,
            Boolean registered,
            Boolean saved,
            String sort,
            /**
             * {@link Integer} rather than {@code int} so an absent parameter can be
             * told apart from an explicit {@code 0}: with a primitive both arrive as
             * 0, which would make a non-zero default page size impossible to express.
             */
            Integer page,
            Integer size) {

        public static final int DEFAULT_PAGE_SIZE = 20;
        public static final int MAX_PAGE_SIZE = 60;

        public boolean includeClosedRequested() {
            return Boolean.TRUE.equals(includeClosed);
        }

        public boolean registeredRequested() {
            return Boolean.TRUE.equals(registered);
        }

        public boolean savedRequested() {
            return Boolean.TRUE.equals(saved);
        }

        public int pageNumber() {
            return page == null || page < 0 ? 0 : page;
        }

        public int pageSize() {
            if (size == null || size < 1) {
                return DEFAULT_PAGE_SIZE;
            }
            return Math.min(size, MAX_PAGE_SIZE);
        }
    }
}