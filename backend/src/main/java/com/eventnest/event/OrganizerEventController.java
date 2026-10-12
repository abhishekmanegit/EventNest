package com.eventnest.event;

import com.eventnest.event.dto.EventDtos.EventDetail;
import com.eventnest.event.dto.EventDtos.EventSummary;
import com.eventnest.event.dto.EventDtos.EventWriteRequest;
import com.eventnest.event.dto.EventDtos.StatusActionRequest;
import com.eventnest.security.SecurityUtils;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Organizer event management (ARCHITECTURE.md section 7.4).
 *
 * <p>{@code @PreAuthorize} here is authorisation <b>layer 2</b>: it proves the
 * caller's role is ORGANIZER. It deliberately says nothing about <em>whose</em>
 * event is being touched - that is layer 3, {@code findByIdAndOrganizerUserId}
 * inside the service, and it is the layer that actually prevents one organizer
 * from editing another's event. Skipping it is the classic broken-object-level
 * authorisation bug; keeping only it would mean any organizer could edit any
 * event. All three layers have to be present.
 *
 * <p>The id in the path is the <b>event</b> id, not a composite. Ownership is
 * derived from the authenticated principal, never accepted from the client -
 * {@code organizerId} is not a parameter anywhere in this class for the same
 * reason.
 */
@RestController
@RequestMapping("/api/v1/organizer/events")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ORGANIZER')")
public class OrganizerEventController {

    private final EventCommandService commandService;

    @GetMapping
    public List<EventSummary> list(@RequestParam(required = false) String status,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        return commandService.mine(status, SecurityUtils.currentUserId(), page, size);
    }

    @GetMapping("/dashboard")
    public EventCommandService.OrganizerDashboard dashboard() {
        return commandService.dashboard(SecurityUtils.currentUserId());
    }

    @GetMapping("/{id}")
    public EventDetail detail(@PathVariable Long id) {
        return commandService.detail(id, SecurityUtils.currentUserId());
    }

    /**
     * {@code publish=true} means "put it in front of students now" - or, for a
     * college that opted into moderation, "queue it for review". The client never
     * chooses the resulting status directly.
     */
    @PostMapping
    public ResponseEntity<EventDetail> create(@Valid @RequestBody EventWriteRequest request,
                                              @RequestParam(defaultValue = "false") boolean publish) {
        EventDetail created = commandService.create(request, publish, SecurityUtils.currentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public EventDetail update(@PathVariable Long id, @Valid @RequestBody EventWriteRequest request) {
        return commandService.update(id, request, SecurityUtils.currentUserId());
    }

    @PatchMapping("/{id}/status")
    public EventDetail changeStatus(@PathVariable Long id,
                                    @Valid @RequestBody StatusActionRequest request) {
        return commandService.changeStatus(id, request, SecurityUtils.currentUserId());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        commandService.delete(id, SecurityUtils.currentUserId());
        return ResponseEntity.noContent().build();
    }
}
