package com.eventnest.event;

import com.eventnest.category.Category;
import com.eventnest.college.College;
import com.eventnest.common.entity.Auditable;
import com.eventnest.organizer.Organizer;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * An event - the centre of the product.
 *
 * <p><b>Three denormalisations, all deliberate</b> (rationale on the columns
 * themselves and in migration V4): {@code college_id}, {@code city} and
 * {@code state} are copied from the organizer at creation time so the hottest
 * query in the product ("published + upcoming, filtered by city/category") stays
 * a single indexed scan instead of a three-table join. {@code registration_count}
 * and {@code view_count} are counters rather than {@code COUNT(*)} subqueries,
 * because counting rows on a list endpoint is an extra full scan per request.
 *
 * <p>The tradeoff is consistency risk, which is why the writes go through
 * {@link EventCommandService} (the only writer) rather than being set ad hoc.
 *
 * <p><b>Why the JPA field types line up exactly with the SQL types.</b>
 * {@code spring.jpa.hibernate.ddl-auto: validate} makes Hibernate compare the
 * entity against the real schema at boot and refuse to start on a mismatch -
 * which is how the {@code display_order} SMALLINT/INTEGER bug was caught. The
 * places where the natural Java mapping would not produce the column we want
 * ({@code TEXT} for long-form strings, {@code text[]}/{@code jsonb} for the
 * array and document columns) are pinned explicitly with
 * {@code columnDefinition}, so the expected type and the actual type are equal
 * by construction instead of by luck. Where even that is not enough - a
 * {@code String} always makes Hibernate expect {@code VARCHAR}, so a
 * {@code CHAR} column can never validate - the schema was changed instead.
 */
@Entity
@Table(name = "events")
@Getter
@Setter
@NoArgsConstructor
public class Event extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** URL segment. Unique, so two events with the same title never collide. */
    @Column(name = "slug", nullable = false, unique = true, length = 220)
    private String slug;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "organizer_id", nullable = false, foreignKey = @ForeignKey(name = "fk_events_organizer"))
    private Organizer organizer;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "college_id", nullable = false, foreignKey = @ForeignKey(name = "fk_events_college"))
    private College college;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "category_id", nullable = false, foreignKey = @ForeignKey(name = "fk_events_category"))
    private Category category;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "summary", length = 300)
    private String summary;

    /**
     * Generated column ({@code title || ' ' || summary}) that the GIN trigram index
     * in V4 is built on. Mapped read-only so search can use {@code ILIKE} against
     * it and hit that index - searching {@code title} and {@code summary} separately
     * with an {@code OR} would leave both indexed columns unusable.
     *
     * <p>{@code insertable/updatable = false} is mandatory: this column is
     * {@code GENERATED ALWAYS AS}, so PostgreSQL rejects any attempt to write it,
     * and JPA would otherwise include it in every INSERT.
     */
    @Column(name = "search_text", insertable = false, updatable = false, length = 700)
    private String searchText;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "poster_url", length = 400)
    private String posterUrl;

    @Column(name = "banner_url", length = 400)
    private String bannerUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 15)
    private EventMode mode = EventMode.OFFLINE;

    @Column(name = "venue", length = 255)
    private String venue;

    @Column(name = "address_line", length = 300)
    private String addressLine;

    @Column(name = "city", length = 80)
    private String city;

    @Column(name = "district", length = 80)
    private String district;

    @Column(name = "state", length = 80)
    private String state;

    @Column(name = "country", nullable = false, length = 80)
    private String country = "India";

    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "registration_deadline", nullable = false)
    private Instant registrationDeadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "registration_mode", nullable = false, length = 15)
    private EventRegistrationMode registrationMode = EventRegistrationMode.INTERNAL;

    @Column(name = "external_registration_url", length = 500)
    private String externalRegistrationUrl;

    @Column(name = "max_participants")
    private Integer maxParticipants;

    @Column(name = "team_size_min")
    private Integer teamSizeMin;

    @Column(name = "team_size_max")
    private Integer teamSizeMax;

    @Column(name = "prize_title", length = 150)
    private String prizeTitle;

    @Column(name = "prize_amount", precision = 12, scale = 2)
    private BigDecimal prizeAmount;

    /** ISO 4217 code, fixed width by meaning rather than by column type - see V4. */
    @Column(name = "prize_currency", nullable = false, length = 3)
    private String prizeCurrency = "INR";

    @Column(name = "prize_description", columnDefinition = "TEXT")
    private String prizeDescription;

    @Column(name = "eligibility", columnDefinition = "TEXT")
    private String eligibility;

    @Column(name = "rules", columnDefinition = "TEXT")
    private String rules;

    /**
     * Agenda as one JSON document, not child rows. It is always read and written
     * whole and never queried by time - so a child table would add a join and an
     * N+1 to every detail page for no query benefit. Kept as a raw
     * {@link JsonNode} rather than a typed list because the only operations we
     * perform on it are "replace the whole thing" and "hand it to the client",
     * and {@code JsonNode} is the case Hibernate's JSON binding supports with no
     * extra configuration.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "schedule", nullable = false, columnDefinition = "jsonb")
    private JsonNode schedule;

    /** Free-form labels. {@code text[]} because tags are arbitrary length. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "tags", nullable = false, columnDefinition = "text[]")
    private String[] tags = new String[0];

    /**
     * A snapshot of who to call, deliberately NOT a foreign key: the real
     * coordinator is usually not a platform user, and if they change jobs last
     * year's events must still show a working contact.
     */
    @Column(name = "coordinator_name", length = 120)
    private String coordinatorName;

    @Column(name = "coordinator_email", length = 255)
    private String coordinatorEmail;

    @Column(name = "coordinator_phone", length = 20)
    private String coordinatorPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EventStatus status = EventStatus.DRAFT;

    /** Inherited from the college at creation (decision D19). */
    @Column(name = "moderation_required", nullable = false)
    private boolean moderationRequired;

    /**
     * Admin "pin to top of discovery" (V11). A property of the event rather than
     * a separate promotion table, so it survives edits and shows in the
     * moderation queue without a join.
     */
    @Column(name = "featured", nullable = false)
    private boolean featured;

    /** Why an admin rejected it, if they did. Null for everything else. */
    @Column(name = "moderation_reason", length = 500)
    private String moderationReason;

    @Column(name = "registration_count", nullable = false)
    private int registrationCount;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", length = 300)
    private String cancellationReason;

    /** Convenience for the many "is this over?" checks in the service layer. */
    public boolean isUpcoming() {
        return endsAt != null && endsAt.isAfter(Instant.now());
    }

    public boolean isRegistrationOpen(Instant now) {
        return status == EventStatus.PUBLISHED
                && now.isBefore(registrationDeadline)
                && isUpcoming();
    }
}