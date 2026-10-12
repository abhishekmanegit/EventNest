package com.eventnest.event;

import com.eventnest.common.dto.PageResponse;
import com.eventnest.event.dto.EventDtos.EventDetail;
import com.eventnest.event.dto.EventDtos.EventQuery;
import com.eventnest.event.dto.EventDtos.EventSummary;
import com.eventnest.event.dto.EventDtos.HomeResponse;
import com.eventnest.security.SecurityUtils;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public discovery surface (ARCHITECTURE.md section 7.2).
 *
 * <p>Read-only and anonymous by design - this is the top of the funnel, and a
 * logged-out visitor has to be able to browse before there is any reason to sign
 * up. Note what is absent: no {@code @PreAuthorize}, because the security config
 * has already matched these paths as public. The only per-request decision here
 * is "who, if anyone, is calling", which the null-safe accessors supply.
 *
 * <p>Deliberately thin: it binds parameters, delegates, and returns. The one
 * thing worth noticing is that {@link #detail} passes the viewer's id and role
 * through rather than resolving visibility itself - whether an unpublished event
 * may be seen is a business rule, so it lives in the service with the rest of
 * them.
 */
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final EventQueryService queryService;

    @GetMapping
    public PageResponse<EventSummary> list(@ModelAttribute @Valid EventQuery query) {
        return queryService.search(query, SecurityUtils.currentUserIdOrNull());
    }

    /** One request for the whole home page - see {@link HomeResponse}. */
    @GetMapping("/home")
    public HomeResponse home() {
        return queryService.home(SecurityUtils.currentUserIdOrNull());
    }

    @GetMapping("/{idOrSlug}")
    public EventDetail detail(@PathVariable String idOrSlug) {
        return queryService.detail(idOrSlug,
                SecurityUtils.currentUserIdOrNull(),
                SecurityUtils.currentUserRoleOrNull());
    }

    @GetMapping("/{idOrSlug}/related")
    public List<EventSummary> related(@PathVariable String idOrSlug) {
        return queryService.related(idOrSlug,
                SecurityUtils.currentUserIdOrNull(),
                SecurityUtils.currentUserRoleOrNull());
    }
}
