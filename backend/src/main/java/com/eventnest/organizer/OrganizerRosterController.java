package com.eventnest.organizer;

import com.eventnest.common.dto.PageResponse;
import com.eventnest.organizer.RosterDtos.RosterRow;
import com.eventnest.organizer.RosterDtos.UpdateRosterRequest;
import com.eventnest.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Roster endpoints (ARCHITECTURE.md §7.4).
 *
 * <p>Ownership is <b>not</b> asserted with {@code @PreAuthorize} here - and that
 * is the point. The URL carries an event id that belongs to some organizer, and
 * no annotation can know whether it belongs to <i>this</i> one; that fact only
 * exists in the database. So the filter chain proves the caller is an organizer
 * at all (layer 1) and the service proves the row is theirs (layer 3). The
 * annotation that would look reassuring here would be decorative, and decorative
 * security is how gaps get shipped.
 */
@RestController
@RequestMapping("/api/v1/organizer")
@PreAuthorize("hasRole('ORGANIZER')")
public class OrganizerRosterController {

    private final OrganizerRosterService rosterService;

    public OrganizerRosterController(OrganizerRosterService rosterService) {
        this.rosterService = rosterService;
    }

    @GetMapping("/events/{eventId}/registrations")
    public PageResponse<RosterRow> roster(
            @PathVariable Long eventId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return rosterService.roster(eventId, SecurityUtils.currentUserId(), status, q,
                page, Math.min(Math.max(1, size), 200));
    }

    /**
     * A CSV response rather than an Excel file: the endpoint needs no POI
     * dependency, opens in every spreadsheet, and is trivially re-usable by a
     * script. {@code filename} is derived from the event id, never from request
     * input, so it cannot be used to smuggle {@code ../} or a header break into
     * {@code Content-Disposition}.
     */
    @GetMapping("/events/{eventId}/registrations/export")
    public ResponseEntity<String> export(@PathVariable Long eventId) {
        String csv = rosterService.exportCsv(eventId, SecurityUtils.currentUserId());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("registrations-" + eventId + ".csv")
                .build());
        return ResponseEntity.ok().headers(headers).body(csv);
    }

    @PatchMapping("/registrations/{registrationId}")
    public RosterRow update(@PathVariable Long registrationId,
                            @Valid @RequestBody UpdateRosterRequest request) {
        return rosterService.update(registrationId, request, SecurityUtils.currentUserId());
    }
}
