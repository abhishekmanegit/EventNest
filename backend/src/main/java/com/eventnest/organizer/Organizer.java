package com.eventnest.organizer;

import com.eventnest.college.College;
import com.eventnest.common.entity.Auditable;
import com.eventnest.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A college's authorized publisher - the supply side of the marketplace.
 *
 * <p>Shared primary key with {@code users} expresses 1:1 (one login, one
 * organizer profile). {@code college_id} expresses the 1:N: one college can have
 * many organizers, which is the normal case once a society or department adopts
 * the platform.
 *
 * <p><b>Note there is no {@code @GeneratedValue} on {@code user_id}.</b> That
 * looks like an oversight but is the point: this column is a foreign key, so its
 * value is the id the {@code users} insert already produced. Letting Hibernate
 * generate it would try to invent a new one and violate the constraint. We
 * assign it explicitly in {@code AuthService} from the saved user - an "assigned"
 * identifier, which is the correct mapping for a shared primary key.
 *
 * <p>{@code FetchType.EAGER} on college is a deliberate, narrow exception to the
 * default lazy strategy: the college name appears on nearly every organizer
 * response and on the event detail page, so lazy loading it would produce either
 * N+1 queries or lazy-init crashes under {@code open-in-view: false}. A scalar
 * (not a collection) is the safe kind to fetch eagerly.
 */
@Entity
@Table(name = "organizers")
@Getter
@Setter
@NoArgsConstructor
public class Organizer extends Auditable {

    @Id
    @Column(name = "user_id")
    private Long userId;

    /**
     * The login this organizer profile belongs to, mapped <b>read-only</b>
     * against the same column as the primary key above.
     *
     * <p>{@code insertable = false, updatable = false} is what makes a shared
     * primary key work as an association: without it JPA would try to write the
     * column twice (once for {@code userId}, once for the foreign key) and fail.
     * With it, every event response can reach the organizer's display name in the
     * same join that already fetches the organizer - without this, showing
     * "by Rahul Sharma" on a list of 20 events would be 20 extra queries.
     *
     * <p>EAGER, same argument as {@code college}: it is a scalar on a path that
     * every list and detail response walks.
     */
    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_organizers_user"))
    private User user;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "college_id", nullable = false, foreignKey = @ForeignKey(name = "fk_organizers_college"))
    private College college;

    @Column(name = "designation", length = 80)
    private String designation;

    @Column(name = "department", length = 120)
    private String department;

    @Column(name = "phone", length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private OrganizerVerificationStatus verificationStatus = OrganizerVerificationStatus.PENDING;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    /** Which admin approved this. Makes the trust decision auditable, not just asserted. */
    @Column(name = "verified_by")
    private Long verifiedBy;

    @Column(name = "rejection_reason", length = 300)
    private String rejectionReason;

    public boolean canPublish() {
        return verificationStatus.canPublish();
    }
}
