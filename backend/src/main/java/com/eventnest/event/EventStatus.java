package com.eventnest.event;

/**
 * Lifecycle of an event (mirrors {@code ck_events_status} in migration V4).
 *
 * <p>Most platforms model publishing as a review queue, which means a student
 * with a deadline on Friday waits for a human on Thursday. We deliberately do
 * not (decision D19): a college is verified once, and after that it publishes
 * directly. {@link #PENDING_REVIEW} and {@link #REJECTED} therefore only occur
 * for colleges an admin has explicitly flagged {@code moderation_required}, and
 * abuse is handled after the fact by reports and takedown.
 */
public enum EventStatus {

    DRAFT("Draft"),
    PENDING_REVIEW("Pending review"),
    PUBLISHED("Published"),
    REJECTED("Rejected"),
    CANCELLED("Cancelled"),
    COMPLETED("Completed");

    private final String label;

    EventStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** True if students can see it in discovery. */
    public boolean isVisibleToStudents() {
        return this == PUBLISHED;
    }

    /** True if it still appears on the organizer's dashboard as active work. */
    public boolean isOpenForEditing() {
        return this == DRAFT || this == PENDING_REVIEW || this == REJECTED;
    }

    /**
     * Is this transition legal? Centralised so the rule lives in one place rather
     * than being re-derived in each controller, service and test.
     */
    public boolean canTransitionTo(EventStatus target) {
        return switch (this) {
            case DRAFT -> target == PUBLISHED || target == PENDING_REVIEW || target == CANCELLED;
            case PENDING_REVIEW -> target == PUBLISHED || target == REJECTED || target == CANCELLED;
            case PUBLISHED -> target == COMPLETED || target == CANCELLED || target == PENDING_REVIEW;
            case REJECTED -> target == DRAFT || target == PENDING_REVIEW || target == CANCELLED;
            // Terminal states: an archived event is never un-cancelled or reopened,
            // because students may already have been told it is off.
            case CANCELLED, COMPLETED -> false;
        };
    }
}