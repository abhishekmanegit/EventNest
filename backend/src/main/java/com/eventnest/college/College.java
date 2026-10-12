package com.eventnest.college;

import com.eventnest.common.entity.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The college directory - a shared reference table (ARCHITECTURE.md section 4.2).
 *
 * <p>Two very different things point at this table: a student's profile
 * ({@code student_profiles.college_id}) and an organizer's claimed affiliation
 * ({@code organizers.college_id}). Sharing one directory means "events by this
 * college" is a single indexed lookup, and the college a student picks is by
 * construction a college that actually exists on the platform.
 */
@Entity
@Table(name = "colleges")
@Getter
@Setter
@NoArgsConstructor
public class College extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 180)
    private String name;

    /** Shareable URL segment. Unique in the database, so a college can never collide. */
    @Column(name = "slug", nullable = false, unique = true, length = 200)
    private String slug;

    @Column(name = "logo_url", length = 400)
    private String logoUrl;

    @Column(name = "website_url", length = 300)
    private String websiteUrl;

    @Column(name = "about", columnDefinition = "TEXT")
    private String about;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private CollegeVerificationStatus verificationStatus = CollegeVerificationStatus.PENDING;

    /**
     * Per-college moderation gate (added in migration V10; decision D19).
     *
     * <p>{@code false} for every college by default, which is the design: a
     * student with a deadline on Friday should not need a human on Thursday.
     * Flipping it on makes that college's events start as
     * {@code PENDING_REVIEW} instead of publishing directly - so the review queue
     * is a real, opt-in feature rather than a tax on everyone.
     *
     * <p>Copied onto {@code events.moderation_required} at creation time rather
     * than read live, so turning moderation on or off later never rewrites the
     * status of events that already exist.
     */
    @Column(name = "moderation_required", nullable = false)
    private boolean moderationRequired;

    @Column(name = "city", nullable = false, length = 80)
    private String city;

    @Column(name = "district", length = 80)
    private String district;

    @Column(name = "state", nullable = false, length = 80)
    private String state;

    @Column(name = "country", nullable = false, length = 80)
    private String country = "India";

    /**
     * Stored now so "within 25 km" can be added later with an additive PostGIS
     * migration instead of a backfill (ARCHITECTURE.md section 4.5). Nullable
     * because a manually added college may not have coordinates yet.
     */
    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    public boolean isVerified() {
        return verificationStatus == CollegeVerificationStatus.VERIFIED;
    }
}
