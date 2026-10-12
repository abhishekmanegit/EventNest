package com.eventnest.me;

import com.eventnest.bookmark.BookmarkService;
import com.eventnest.common.dto.PageResponse;
import com.eventnest.event.dto.EventDtos.EventSummary;
import com.eventnest.registration.RegistrationQueryService;
import com.eventnest.registration.dto.RegistrationDtos.MyRegistrationRow;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Everything the signed-in student owns that is <em>not</em> the event itself:
 * their registrations and their saved events (ARCHITECTURE.md §7, "App" routes).
 *
 * <p>Grouped under {@code /me} rather than spread across
 * {@code /registrations} and {@code /bookmarks} because both are answer-shaped
 * "what did I do?" questions. Flat top-level collections would read as though the
 * platform were letting one student list <em>everyone's</em> registrations; the
 * prefix says the opposite before the handler is even reached - and the
 * {@code hasRole('STUDENT')} matcher says it again at the filter chain.
 */
@RestController
@RequestMapping("/api/v1/me")
@PreAuthorize("hasRole('STUDENT')")
public class MeController {

    private final RegistrationQueryService registrationQueryService;
    private final BookmarkService bookmarkService;

    public MeController(RegistrationQueryService registrationQueryService,
                        BookmarkService bookmarkService) {
        this.registrationQueryService = registrationQueryService;
        this.bookmarkService = bookmarkService;
    }

    /** Newest first, cancelled rows included - see {@link RegistrationQueryService#mine}. */
    @GetMapping("/registrations")
    public PageResponse<MyRegistrationRow> registrations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return registrationQueryService.mine(page, clamp(size));
    }

    @GetMapping("/bookmarks")
    public PageResponse<EventSummary> bookmarks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return bookmarkService.mine(page, clamp(size));
    }

    /**
     * Page size is client input, so it is bounded rather than trusted. Without
     * this, {@code ?size=100000} is a free way to make the server load a table.
     */
    private static int clamp(int size) {
        return Math.min(Math.max(1, size), 60);
    }
}
