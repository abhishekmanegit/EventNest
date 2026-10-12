package com.eventnest.registration;

import com.eventnest.registration.dto.RegistrationDtos.MyRegistrationStatus;
import com.eventnest.registration.dto.RegistrationDtos.RegistrationResult;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Student registration endpoints (ARCHITECTURE.md 7.3).
 *
 * <p>These paths are deliberately <b>not</b> under {@code /organizer} or a
 * {@code /me} prefix: the resource being acted on is the event's roster entry,
 * so the event is the right thing to nest them under. The role gate comes from
 * the filter chain ({@code hasRole('STUDENT')}) rather than {@code @PreAuthorize}
 * here, because there is no ownership question to answer - a student can only
 * ever act on their own row, and that is enforced by every repository method
 * taking {@code userId} as a parameter.
 */
@RestController
@RequestMapping("/api/v1/events/{eventId}")
@Validated
@PreAuthorize("hasRole('STUDENT')")
public class RegistrationController {

    private final RegistrationCommandService commandService;
    private final RegistrationQueryService queryService;

    public RegistrationController(RegistrationCommandService commandService,
                                  RegistrationQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    /** 201 with the registration number and a summary the UI can repaint from. */
    @PostMapping("/register")
    public ResponseEntity<RegistrationResult> register(@PathVariable Long eventId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(commandService.register(eventId));
    }

    /**
     * 204, and idempotent in the way clients need: cancelling an event you are
     * already not registered for is a no-op success, because the caller's intent
     * ("I am not going") is satisfied either way.
     */
    @DeleteMapping("/register")
    public ResponseEntity<Void> cancel(@PathVariable Long eventId,
                                       @RequestParam(required = false)
                                       @Size(max = 200) String reason) {
        commandService.cancel(eventId, reason);
        return ResponseEntity.noContent().build();
    }

    /** 200 when registered, 204 with no body otherwise - see {@link MyRegistrationStatus}. */
    @GetMapping("/registration")
    public ResponseEntity<MyRegistrationStatus> myRegistration(@PathVariable Long eventId) {
        return queryService.myStatus(eventId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
