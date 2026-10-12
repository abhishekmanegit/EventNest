package com.eventnest.notification;

import com.eventnest.config.AppProperties;
import com.eventnest.event.Event;
import com.eventnest.event.EventRepository;
import com.eventnest.event.EventStatus;
import com.eventnest.registration.RegistrationRepository;
import com.eventnest.registration.RegistrationStatus;
import com.eventnest.security.RefreshTokenRepository;
import com.eventnest.student.StudentProfile;
import com.eventnest.student.StudentProfileRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fan-out of the three time-or-publish driven notifications.
 *
 * <p><b>Why "insert or ignore", never "check then insert".</b> Reminders are
 * periodic; every run re-reads the same window of events. If the application
 * decided "already sent?" by SELECTing first, a crash between the SELECT and the
 * INSERT, or two overlapping scheduler ticks, would spam students. The
 * {@code UNIQUE (dedupe_key)} on {@code notifications} makes the second write a
 * no-op inside the database - the retry is not merely unlikely to duplicate, it
 * cannot duplicate. See {@code NotificationService} for the same contract used
 * by the synchronous fan-outs (registration etc.).
 *
 * <p><b>Why one transaction per tick?</b> The scan finds events, then inserts one
 * row per affected student. Splitting that into per-event transactions would
 * leave "half fan-out" as the committed state on a crash; one transaction means
 * a tick is all-or-nothing, which is exactly the granularity the in-page bell can
 * tolerate (the next tick simply produces nothing new).
 *
 * <p>The whole class is gated on {@code app.notifications.enabled}, checked on
 * every fire rather than as a bean condition, so wiring stays uniform and the
 * flag stays the single switch. In tests the flag is off and no background tick
 * can surprise an assertion that counts rows.
 */
@Component
@RequiredArgsConstructor
public class EventNotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EventNotificationDispatcher.class);
    private static final int STARTING_WINDOW_MINUTES = 60;
    private static final int DEADLINE_WINDOW_HOURS = 24;

    private final NotificationService notificationService;
    private final EventRepository eventRepository;
    private final RegistrationRepository registrationRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AppProperties properties;
    private final Clock clock;

    // ------------------------------------------------------------------ fires --

    /**
     * Called from the publish paths in {@code EventCommandService}. Joins that
     * caller's transaction, so a match notification exists if and only if the
     * publish that caused it committed.
     */
    @Transactional
    public void onPublished(Event event) {
        if (!enabled()) {
            return;
        }
        for (Long studentId : matchingStudentIds(event)) {
            notificationService.newEventMatch(event, studentId);
        }
    }

    /** Every minute: events starting within the next hour. */
    @Scheduled(fixedDelay = 60_000, initialDelay = 15_000)
    @Transactional
    public void sendStartingReminders() {
        if (!enabled()) {
            return;
        }
        Instant now = Instant.now(clock);
        List<Event> starting = eventRepository.findByStatusAndStartsAtBetween(
                EventStatus.PUBLISHED, now, now.plusSeconds((long) STARTING_WINDOW_MINUTES * 60));
        for (Event event : starting) {
            for (Long userId : confirmedUserIds(event)) {
                notificationService.eventStarting(event, userId);
            }
        }
        if (!starting.isEmpty()) {
            log.info("Sent {} starting-soon fan-outs", starting.size());
        }
    }

    /** Every 15 minutes: registrations closing within the next day. */
    @Scheduled(fixedDelay = 900_000, initialDelay = 30_000)
    @Transactional
    public void sendDeadlineReminders() {
        if (!enabled()) {
            return;
        }
        Instant now = Instant.now(clock);
        List<Event> closing = eventRepository.findByStatusAndRegistrationDeadlineBetween(
                EventStatus.PUBLISHED, now, now.plusSeconds((long) DEADLINE_WINDOW_HOURS * 60 * 60));
        for (Event event : closing) {
            for (Long userId : confirmedUserIds(event)) {
                notificationService.deadlineReminder(event, userId);
            }
        }
        if (!closing.isEmpty()) {
            log.info("Sent {} deadline fan-outs", closing.size());
        }
    }

    /**
     * Housekeeping, deliberately decoupled from the academic-reminder cadence:
     * expired refresh tokens can never be valid again, so historically they are
     * a monotonically growing table. Deleting once a day at the quiet hour keeps
     * it from being the thing that grows forever.
     */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeExpiredSessions() {
        refreshTokenRepository.deleteExpiredBefore(Instant.now(clock).minusSeconds(3600));
    }

    // ------------------------------------------------------------- helpers ----

    private boolean enabled() {
        return properties.notifications().enabled();
    }

    /**
     * Students the event is "near" - same city, or same state when the event is
     * not city-specific. City wins when both are set: "Pune + Maharashtra" is a
     * smaller, more relevant circle than "everyone in Maharashtra".
     */
    private Set<Long> matchingStudentIds(Event event) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (isNotBlank(event.getCity())) {
            collect(studentProfileRepository.findByCityIgnoreCase(event.getCity().trim()), ids);
        }
        if (isNotBlank(event.getState())) {
            collect(studentProfileRepository.findByStateIgnoreCase(event.getState().trim()), ids);
        }
        return ids;
    }

    private static void collect(List<StudentProfile> profiles, Set<Long> ids) {
        for (StudentProfile profile : profiles) {
            ids.add(profile.getUserId());
        }
    }

    private List<Long> confirmedUserIds(Event event) {
        return registrationRepository.findUserIdsByEventIdAndStatus(
                event.getId(), RegistrationStatus.CONFIRMED);
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}