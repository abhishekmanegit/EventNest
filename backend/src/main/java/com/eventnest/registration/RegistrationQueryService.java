package com.eventnest.registration;

import com.eventnest.common.dto.PageResponse;
import com.eventnest.event.EventQueryService;
import com.eventnest.event.dto.EventDtos.EventSummary;
import com.eventnest.registration.dto.RegistrationDtos.MyRegistrationRow;
import com.eventnest.registration.dto.RegistrationDtos.MyRegistrationStatus;
import com.eventnest.security.SecurityUtils;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of registration: "am I going?" and "what am I going to?".
 *
 * <p>Split from {@link RegistrationCommandService} for the same reason the two
 * event services are split - reads here are two lookups and a mapping, and they
 * have no business sharing a transaction with seat claiming.
 */
@Service
@RequiredArgsConstructor
public class RegistrationQueryService {

    private final RegistrationRepository registrationRepository;
    private final EventQueryService eventQueryService;
    private final Clock clock;

    /**
     * {@code GET /events/{id}/registration}. Empty means "never registered", which
     * the controller turns into {@code 204 No Content} rather than a 404 - see the
     * javadoc on {@link MyRegistrationStatus}.
     */
    @Transactional(readOnly = true)
    public Optional<MyRegistrationStatus> myStatus(Long eventId) {
        Long userId = SecurityUtils.currentUserId();
        return registrationRepository.findByEventIdAndUserId(eventId, userId)
                .map(registration -> new MyRegistrationStatus(
                        registration.getRegistrationNumber(),
                        registration.getStatus(),
                        registration.getCreatedAt(),
                        registration.getCancelledAt(),
                        isCancellable(registration)));
    }

    /**
     * {@code GET /me/registrations} - every registration, cancelled ones included.
     *
     * <p>Showing the cancellations is intentional: a student who cancelled by
     * mistake needs to see that it happened before they assume the platform lost
     * their seat. The status column carries the distinction, so the list does not
     * have to.
     */
    @Transactional(readOnly = true)
    public PageResponse<MyRegistrationRow> mine(int page, int size) {
        Long userId = SecurityUtils.currentUserId();
        Page<Registration> rows = registrationRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));

        List<EventSummary> summaries = eventQueryService.summarize(
                rows.getContent().stream().map(Registration::getEvent).toList(), userId);

        // Indexed rather than indexOf(): identity-based search would be O(n^2) and
        // would silently mis-pair if two rows ever compared equal.
        List<Registration> registrations = rows.getContent();
        List<MyRegistrationRow> content = new java.util.ArrayList<>(registrations.size());
        for (int i = 0; i < registrations.size(); i++) {
            Registration registration = registrations.get(i);
            content.add(new MyRegistrationRow(
                    registration.getRegistrationNumber(),
                    registration.getStatus(),
                    registration.getCreatedAt(),
                    registration.getCancelledAt(),
                    summaries.get(i)));
        }
        return PageResponse.of(rows, content);
    }

    /**
     * Cancellation is offered until the event starts, not until it ends - after
     * that the seat is part of the organizer's headcount on the day and the
     * student has to talk to a person (the service enforces the same rule, this
     * only decides whether the button is drawn).
     */
    private boolean isCancellable(Registration registration) {
        return registration.isActive()
                && Instant.now(clock).isBefore(registration.getEvent().getStartsAt());
    }
}
