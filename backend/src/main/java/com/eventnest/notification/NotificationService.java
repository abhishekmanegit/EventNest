package com.eventnest.notification;

import com.eventnest.event.Event;
import com.eventnest.user.User;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Writes notification rows. <b>Deliberately not {@code @Transactional}.</b>
 *
 * <p>Every notification here is a side effect of a business action that already
 * has its own transaction (a registration, a cancellation, an approval). Running
 * in the caller's transaction is what gives the guarantee that matters: the
 * notification exists if and only if the action it describes committed. A
 * separate transaction would need a mechanism to delete the notification when
 * the action rolls back, which is strictly more machinery for a strictly worse
 * outcome.
 *
 * <p>The failure mode to avoid - "notification failed, so the student's
 * registration did not go through" - is therefore a real risk, and it is why
 * nothing here validates, enriches or can throw on ordinary input: these are
 * straight inserts of already-known strings.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    /** Characters lost to truncation, so titles/bodies are cut before the DB does it. */
    private static final int TITLE_MAX = 150;
    private static final int BODY_MAX = 500;

    private final NotificationRepository notificationRepository;

    public void registrationConfirmed(Event event, User student) {
        insert(student, event, NotificationType.REGISTRATION_CONFIRMED,
                "Registration confirmed",
                "You're registered for " + event.getTitle()
                        + ". Your registration number is shown on the event page.",
                "/events/" + event.getSlug(),
                dedupeKey("reg", event, student.getId()));
    }

    public void registrationCancelled(Event event, User student) {
        insert(student, event, NotificationType.REGISTRATION_CANCELLED,
                "Registration cancelled",
                "Your registration for " + event.getTitle() + " has been cancelled.",
                "/events/" + event.getSlug(),
                // A distinct prefix from the confirmation: both may exist for the
                // same (event, student) pair, and the column is UNIQUE across the
                // whole table - not per type.
                dedupeKey("regcancel", event, student.getId()));
    }

    public void newRegistration(Event event, User organizer, User student) {
        insert(organizer.getId(), event, NotificationType.NEW_REGISTRATION,
                "New registration",
                student.getFullName() + " registered for " + event.getTitle() + ".",
                "/organizer/events/" + event.getId(),
                dedupeKey("regnew", event, student.getId()));
    }

    /**
     * Scheduled: an event the student is registered for starts within the hour.
     *
     * <p>Deliberately per-(event, student) dedupe rather than per-event: a
     * "starting soon" pushed as one fan-out row would mark the whole fan-out as
     * sent for everyone the instant the scheduler interrupted half-way through.
     */
    public void eventStarting(Event event, Long studentId) {
        insert(studentId, event, NotificationType.EVENT_STARTING,
                "Starting soon",
                event.getTitle() + " starts in under an hour. Don't be late!",
                "/events/" + event.getSlug(),
                dedupeKey("start", event, studentId));
    }

    /**
     * Scheduled: registration for an event the student is registered for closes
     * within the next day. Students already signed up get a nudge in case they
     * added a teammate later, or merely want to know they are locked in.
     */
    public void deadlineReminder(Event event, Long studentId) {
        insert(studentId, event, NotificationType.DEADLINE_REMINDER,
                "Registration closing soon",
                "Registration for " + event.getTitle() + " ends tomorrow.",
                "/events/" + event.getSlug(),
                dedupeKey("deadline", event, studentId));
    }

    /**
     * Fired the moment an event is published, to students whose city or state
     * matches its location. The event is new and they have probably not seen it;
     * the match is coarse (location only) because student category interests are
     * not captured yet.
     */
    public void newEventMatch(Event event, Long studentId) {
        insert(studentId, event, NotificationType.NEW_EVENT_MATCH,
                "New event near you",
                event.getTitle() + " is happening in " + (event.getCity() == null ? event.getState() : event.getCity())
                        + ". Check it out.",
                "/events/" + event.getSlug(),
                dedupeKey("newmatch", event, studentId));
    }

    private void insert(User user, Event event, NotificationType type, String title,
                        String body, String actionUrl, String dedupeKey) {
        insert(user.getId(), event, type, title, body, actionUrl, dedupeKey);
    }

    private void insert(Long userId, Event event, NotificationType type, String title,
                        String body, String actionUrl, String dedupeKey) {
        notificationRepository.insertIfAbsent(
                userId,
                event == null ? null : event.getId(),
                type.name(),
                clip(title, TITLE_MAX),
                clip(body, BODY_MAX),
                clip(actionUrl, 255),
                dedupeKey);
    }

    /**
     * {@code reg:123:456} - scoped to the event and the student, which is exactly
     * the pair the confirmation is about. Keeping it short and deterministic means
     * a duplicate write is recognisable in a {@code SELECT *} dump rather than
     * being an opaque hash.
     */
    private static String dedupeKey(String prefix, Event event, Long userId) {
        return String.format(Locale.ROOT, "%s:%d:%d", prefix, event.getId(), userId);
    }

    private static String clip(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
