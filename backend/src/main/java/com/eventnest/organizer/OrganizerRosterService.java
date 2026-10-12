package com.eventnest.organizer;

import com.eventnest.common.dto.PageResponse;
import com.eventnest.common.exception.BadRequestException;
import com.eventnest.common.exception.ConflictException;
import com.eventnest.common.exception.ErrorCode;
import com.eventnest.common.exception.ForbiddenException;
import com.eventnest.common.exception.NotFoundException;
import com.eventnest.event.Event;
import com.eventnest.event.EventRepository;
import com.eventnest.organizer.RosterDtos.RosterRow;
import com.eventnest.organizer.RosterDtos.RosterStatus;
import com.eventnest.organizer.RosterDtos.UpdateRosterRequest;
import com.eventnest.registration.Registration;
import com.eventnest.registration.RegistrationRepository;
import com.eventnest.registration.RegistrationStatus;
import com.eventnest.student.StudentProfile;
import com.eventnest.student.StudentProfileRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The roster: who signed up for an event, and marking them present.
 *
 * <p>Ownership is checked by querying with {@code organizerUserId} in the
 * <b>WHERE clause</b> rather than loading the event and comparing afterwards.
 * That is not a micro-optimisation - it is the difference between "another
 * organizer's event id returns 404" being enforced by the database on every code
 * path, versus being a rule each new method has to remember. An organizer
 * probing ids gets the same 404 as a genuinely missing event, so the endpoint
 * does not even leak which ids exist.
 */
@Service
@RequiredArgsConstructor
public class OrganizerRosterService {

    private final RegistrationRepository registrationRepository;
    private final EventRepository eventRepository;
    private final StudentProfileRepository studentProfileRepository;

    @Transactional(readOnly = true)
    public PageResponse<RosterRow> roster(Long eventId, Long organizerUserId,
                                          String status, String q, int page, int size) {
        requireOwned(eventId, organizerUserId);
        Page<Registration> rows = registrationRepository.findAll(
                rosterSpec(eventId, status, q),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        // Exactly two queries for a page of N rows: the page itself, then one
        // IN (...) for the profiles. Accessing profile.course lazily per row
        // would be N+1 - 21 selects to render a table of 20.
        Map<Long, StudentProfile> profiles = profileMap(rows.getContent());
        List<RosterRow> mapped = rows.getContent().stream()
                .map(registration -> toRow(registration, profiles.get(registration.getUser().getId())))
                .toList();
        return PageResponse.of(rows, mapped);
    }

    /**
     * ATTENDED / DISQUALIFIED only. The status is stamped here rather than taken
     * from the client so {@code checkedInAt} is always set by the same clock the
     * rest of the service uses - which is what makes the column testable.
     */
    @Transactional
    public RosterRow update(Long registrationId, UpdateRosterRequest request, Long organizerUserId) {
        Registration registration = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new NotFoundException("Registration not found: " + registrationId));
        requireOwned(registration.getEvent().getId(), organizerUserId);

        if (registration.getStatus() == RegistrationStatus.CANCELLED) {
            throw new ConflictException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Cannot change a cancelled registration");
        }
        RegistrationStatus target = RegistrationStatus.valueOf(request.status().name());
        registration.setStatus(target);
        registration.setNotes(request.notes() == null ? null : request.notes().trim());
        if (target == RegistrationStatus.ATTENDED && registration.getCheckedInAt() == null) {
            registration.setCheckedInAt(Instant.now());
        }
        Registration saved = registrationRepository.save(registration);
        return toRow(saved, profileMap(List.of(saved)).get(saved.getUser().getId()));
    }

    /**
     * CSV, written by hand rather than with a library.
     *
     * <p>Two things make this non-trivial and both are handled: a leading
     * apostrophe (Excel treats {@code =cmd} as a formula, and a student named
     * {@code =1+1} should not be able to execute anything in the organizer's
     * spreadsheet) and embedded commas/quotes/newlines, which the {@code "a","b"}
     * quoting rule below covers exactly per RFC 4180.
     *
     * <p>{@code \uFEFF} (the UTF-8 BOM) is prefixed so Excel on Windows stops
     * mangling non-ASCII names - a real bug that shows up the first time an
     * organizer exports a roster containing Devanagari.
     */
    @Transactional(readOnly = true)
    public String exportCsv(Long eventId, Long organizerUserId) {
        requireOwned(eventId, organizerUserId);
        List<Registration> rows = registrationRepository
                .findByEventIdOrderByCreatedAtAsc(eventId, PageRequest.of(0, 10_000))
                .getContent();
        Map<Long, StudentProfile> profiles = profileMap(rows);

        StringBuilder csv = new StringBuilder(2048);
        csv.append('\uFEFF');
        csv.append("registration_number,status,registered_at,student_name,email,course,"
                + "graduation_year,checked_in_at,notes\r\n");
        for (Registration registration : rows) {
            StudentProfile profile = profiles.get(registration.getUser().getId());
            csv.append(String.join(",",
                    cell(registration.getRegistrationNumber()),
                    cell(registration.getStatus().name()),
                    cell(registration.getCreatedAt().toString()),
                    cell(registration.getUser().getFullName()),
                    cell(registration.getUser().getEmail()),
                    cell(profile == null ? null : profile.getCourse()),
                    cell(profile == null || profile.getGraduationYear() == null
                            ? null : profile.getGraduationYear().toString()),
                    cell(registration.getCheckedInAt() == null
                            ? null : registration.getCheckedInAt().toString()),
                    cell(registration.getNotes()))).append("\r\n");
        }
        return csv.toString();
    }

    // ------------------------------------------------------------------ helpers --

    private void requireOwned(Long eventId, Long organizerUserId) {
        if (eventRepository.findByIdAndOrganizerUserId(eventId, organizerUserId).isEmpty()) {
            throw new NotFoundException("Event not found: " + eventId);
        }
    }

    private static Specification<Registration> rosterSpec(Long eventId, String status, String q) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("event").get("id"), eventId));

            if (status != null && !status.isBlank()) {
                RegistrationStatus parsed;
                try {
                    parsed = RegistrationStatus.valueOf(status.trim().toUpperCase());
                } catch (IllegalArgumentException ex) {
                    // An unknown filter is a client bug, not a 500 - but it must
                    // not silently return "everything" either, or the UI would
                    // show a filtered-looking table with unfiltered data.
                    throw new BadRequestException("Unknown status filter: " + status);
                }
                predicates.add(cb.equal(root.get("status"), parsed));
            }
            if (q != null && !q.isBlank()) {
                String needle = "%" + q.trim().toLowerCase() + "%";
                var name = cb.lower(root.get("user").<String>get("fullName"));
                var email = cb.lower(root.get("user").<String>get("email"));
                var number = cb.lower(root.get("registrationNumber"));
                predicates.add(cb.or(cb.like(name, needle), cb.like(email, needle),
                        cb.like(number, needle)));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private Map<Long, StudentProfile> profileMap(List<Registration> rows) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        List<Long> userIds = rows.stream()
                .map(registration -> registration.getUser().getId())
                .distinct()
                .toList();
        return studentProfileRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(StudentProfile::getUserId, Function.identity(), (a, b) -> a));
    }

    private static RosterRow toRow(Registration registration, StudentProfile profile) {
        return new RosterRow(
                registration.getId(),
                registration.getRegistrationNumber(),
                registration.getStatus(),
                registration.getCreatedAt(),
                registration.getCancelledAt(),
                registration.getCheckedInAt(),
                registration.getUser().getId(),
                registration.getUser().getFullName(),
                registration.getUser().getEmail(),
                profile == null ? null : profile.getCourse(),
                profile == null ? null : profile.getGraduationYear(),
                registration.getNotes());
    }

    /** Quotes exactly one CSV field, or returns "" for a null - never a comma. */
    private static String cell(String value) {
        if (value == null) {
            return "";
        }
        // Neutralise spreadsheet formula injection: a leading =, +, - or @ makes
        // Excel/Sheets evaluate the cell instead of displaying it.
        String safe = value;
        if (safe.startsWith("=") || safe.startsWith("+") || safe.startsWith("-")
                || safe.startsWith("@")) {
            safe = "'" + safe;
        }
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n")
                || safe.contains("\r")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }
}
