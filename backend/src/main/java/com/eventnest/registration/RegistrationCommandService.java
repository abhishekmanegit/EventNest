package com.eventnest.registration;

import com.eventnest.common.exception.AppException;
import com.eventnest.common.exception.ConflictException;
import com.eventnest.common.exception.ErrorCode;
import com.eventnest.common.exception.NotFoundException;
import com.eventnest.common.util.RegistrationNumberUtil;
import com.eventnest.event.Event;
import com.eventnest.event.EventRepository;
import com.eventnest.event.EventStatus;
import com.eventnest.event.EventQueryService;
import com.eventnest.event.dto.EventDtos.EventSummary;
import com.eventnest.notification.NotificationService;
import com.eventnest.registration.dto.RegistrationDtos.RegistrationResult;
import com.eventnest.security.SecurityUtils;
import com.eventnest.user.User;
import com.eventnest.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Write side of registration: seat claiming, cancellation and the state rules
 * around both (ARCHITECTURE.md section 5.5).
 *
 * <p><b>Two independent guarantees prevent a double registration</b>, and it is
 * worth being precise about which does what:
 * <ul>
 *   <li>{@code UNIQUE (event_id, user_id)} in migration V5 is the only race-free
 *       one. A Java {@code if (!exists)} has a TOCTOU window in which two
 *       concurrent requests both pass.</li>
 *   <li>The {@code findByEventIdAndUserId} pre-check exists purely to answer
 *       {@code 409 ALREADY_REGISTERED} with a sentence a human can read, instead
 *       of a raw constraint violation.</li>
 * </ul>
 * Belt and braces, and the belt (the constraint) is the one that counts.
 *
 * <p>The capacity claim works the same way - see {@code tryReserveSeat} for why
 * a conditional {@code UPDATE} beats a count-then-insert.
 */
@Service
@RequiredArgsConstructor
public class RegistrationCommandService {

    /** A collision on a 40-bit number is ~1 in a trillion; five tries is already generous. */
    private static final int NUMBER_GENERATION_ATTEMPTS = 5;

    private final EventRepository eventRepository;
    private final RegistrationRepository registrationRepository;
    private final UserRepository userRepository;
    private final EventQueryService eventQueryService;
    private final NotificationService notificationService;
    private final Clock clock;

    /**
     * Injected rather than obtained from the transaction, because the only reason
     * we need it is to {@code refresh()} an entity after a bulk UPDATE has moved
     * the row underneath Hibernate. Not final, so {@code @RequiredArgsConstructor}
     * leaves it alone and {@code @PersistenceContext} fills it in.
     */
    @PersistenceContext
    private EntityManager entityManager;

    /**
     * {@code POST /api/v1/events/{id}/register} → 201.
     *
     * <p>Guard order is deliberate: cheap and specific checks first, the one that
     * mutates database state last. Every failure below throws a
     * {@link ConflictException} (a {@code RuntimeException}), so the seat
     * reservation in {@link #reserveSeat} - which has already run by the time the
     * INSERT could fail - is rolled back with everything else. A rejected
     * registration therefore never permanently burns a seat, which is the failure
     * mode that would otherwise quietly make an event "full" with nobody in it.
     */
    @Transactional
    public RegistrationResult register(Long eventId) {
        Long userId = SecurityUtils.currentUserId();
        Instant now = Instant.now(clock);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found: " + eventId));

        // 404 already answered "does it exist?"; this answers "may I register?".
        // DRAFT, PENDING_REVIEW, REJECTED, CANCELLED and COMPLETED all land here.
        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new ConflictException(ErrorCode.EVENT_NOT_PUBLISHED,
                    "Registration is not open for this event.");
        }
        if (!now.isBefore(event.getRegistrationDeadline())) {
            throw new ConflictException(ErrorCode.REGISTRATION_CLOSED,
                    "Registration for this event closed on " + event.getRegistrationDeadline() + ".");
        }
        if (!now.isBefore(event.getStartsAt())) {
            throw new ConflictException(ErrorCode.EVENT_ALREADY_STARTED,
                    "This event has already started.");
        }

        // Fast path. Not the guarantee - see the class javadoc.
        Registration existing = registrationRepository
                .findByEventIdAndUserId(eventId, userId).orElse(null);
        if (existing != null && existing.isActive()) {
            throw new ConflictException(ErrorCode.ALREADY_REGISTERED,
                    "You are already registered for this event.");
        }

        reserveSeat(eventId);

        // Re-read: the capacity UPDATE moved the row, so the in-memory event is
        // now stale by exactly one. Refreshing keeps the entity managed (unlike
        // clearAutomatically) so the Registration we are about to insert still
        // points at a live object rather than a detached one.
        entityManager.refresh(event);

        Registration registration;
        if (existing != null) {
            registration = reactivate(existing);
        } else {
            registration = new Registration();
            registration.setEvent(event);
            registration.setUser(userRepository.findById(userId)
                    .orElseThrow(() -> new NotFoundException("User not found: " + userId)));
            registration.setStatus(RegistrationStatus.CONFIRMED);
            registration.setRegistrationNumber(freshRegistrationNumber());
            registration = registrationRepository.saveAndFlush(registration);
        }

        notify(event, registration, userId);
        return result(event, registration);
    }

    /**
     * {@code DELETE /api/v1/events/{id}/register} → 204.
     *
     * <p>The row is updated to {@code CANCELLED}, never deleted. The organizer's
     * roster is an audit trail, and {@code UNIQUE (event_id, user_id)} means a
     * later re-registration must reuse this row anyway - deleting it would
     * silently erase who showed up and who did not.
     */
    @Transactional
    public void cancel(Long eventId, String reason) {
        Long userId = SecurityUtils.currentUserId();
        Instant now = Instant.now(clock);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found: " + eventId));

        Registration registration = registrationRepository
                .findByEventIdAndUserId(eventId, userId)
                .filter(Registration::isActive)
                .orElseThrow(() -> new ConflictException(ErrorCode.CANNOT_CANCEL_REGISTRATION,
                        "You are not registered for this event."));

        if (!now.isBefore(event.getStartsAt())) {
            throw new ConflictException(ErrorCode.CANNOT_CANCEL_REGISTRATION,
                    "This event has already started - please contact the coordinator instead.");
        }

        registration.cancel(now, reason);
        registrationRepository.saveAndFlush(registration);
        eventRepository.adjustRegistrationCount(eventId, -1);
        entityManager.refresh(event);

        User student = registration.getUser();
        notificationService.registrationCancelled(event, student);
    }

    // ------------------------------------------------------------- internals --

    /**
     * Decision D9 in one statement: test-and-claim, atomically. Returns the number
     * of rows the database actually updated, which is the only race-free way to
     * know whether a seat was still available.
     */
    private void reserveSeat(Long eventId) {
        if (eventRepository.tryReserveSeat(eventId) != 1) {
            throw new ConflictException(ErrorCode.EVENT_FULL,
                    "This event is full.");
        }
    }

    /**
     * Re-activating a previously cancelled registration. The UPDATE is conditional
     * on the row still being {@code CANCELLED} so that two concurrent
     * re-registrations cannot both succeed and double-count the seat - the loser
     * matches zero rows, throws, and rolls its reservation back.
     */
    private Registration reactivate(Registration existing) {
        if (registrationRepository.reactivateIfCancelled(
                existing.getId(), RegistrationStatus.CONFIRMED, RegistrationStatus.CANCELLED) != 1) {
            throw new ConflictException(ErrorCode.ALREADY_REGISTERED,
                    "You are already registered for this event.");
        }
        entityManager.refresh(existing);
        return existing;
    }

    private void notify(Event event, Registration registration, Long userId) {
        User student = registration.getUser();
        notificationService.registrationConfirmed(event, student);
        if (event.getOrganizer() != null && event.getOrganizer().getUser() != null) {
            // Not sent when the organizer is registering for their own event -
            // a "someone registered" notification you triggered yourself is noise.
            if (!event.getOrganizer().getUser().getId().equals(userId)) {
                notificationService.newRegistration(event, event.getOrganizer().getUser(), student);
            }
        }
    }

    private RegistrationResult result(Event event, Registration registration) {
        List<EventSummary> summaries = eventQueryService.summarize(List.of(event), SecurityUtils.currentUserId());
        return new RegistrationResult(
                registration.getRegistrationNumber(),
                registration.getStatus(),
                summaries.get(0));
    }

    /**
     * Generated <em>before</em> the flush and checked against the column, rather
     * than generated, flushed, and retried on violation: a constraint failure
     * mid-transaction marks the transaction rollback-only in JPA, so there is
     * nothing left to retry into. See {@code RegistrationNumberUtil} for why the
     * alphabet looks the way it does.
     */
    private String freshRegistrationNumber() {
        for (int attempt = 0; attempt < NUMBER_GENERATION_ATTEMPTS; attempt++) {
            String candidate = RegistrationNumberUtil.generate();
            if (!registrationRepository.existsByRegistrationNumber(candidate)) {
                return candidate;
            }
        }
        throw new AppException(ErrorCode.INTERNAL_ERROR,
                "Could not allocate a registration number. Please try again.");
    }
}
