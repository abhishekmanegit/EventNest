package com.eventnest.college;

import com.eventnest.college.CollegeDtos.CollegeCreateRequest;
import com.eventnest.college.CollegeDtos.CollegeDetail;
import com.eventnest.college.CollegeDtos.CollegeListItem;
import com.eventnest.college.CollegeDtos.CollegeOption;
import com.eventnest.common.dto.PageResponse;
import com.eventnest.event.dto.EventDtos.EventSummary;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public college directory (ARCHITECTURE.md section 7.2).
 *
 * <p>Anonymous, like all discovery: a student choosing a college has not signed
 * up yet, and the directory is one of the few surfaces that can convince them to.
 *
 * <p>{@code POST /colleges} is the exception that proves the rule - it exists so
 * a not-yet-registered student can add the college their form was missing. It
 * creates a PENDING row (kept out of the verified directory) and stays public
 * for the same reason the GETs are public: the form runs before authentication.
 */
@RestController
@RequestMapping("/api/v1/colleges")
@RequiredArgsConstructor
public class CollegeController {

    private final CollegeQueryService queryService;
    private final CollegeCommandService commandService;

    @GetMapping
    public PageResponse<CollegeListItem> list(@RequestParam(required = false) String q,
                                              @RequestParam(required = false) String state,
                                              @RequestParam(required = false) Boolean verified,
                                              @RequestParam(required = false) Boolean includePending,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return queryService.list(q, state, verified, includePending, page, size);
    }

    /**
     * The "Other…" branch of a college dropdown: create (or return the existing)
     * college from a typed name, so registration links to a real directory row
     * and the next typist finds the name already suggested.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CollegeOption create(@RequestBody CollegeCreateRequest request) {
        return commandService.create(request);
    }

    /** Accepts an id or a slug, exactly like the event detail endpoint. */
    @GetMapping("/{idOrSlug}")
    public CollegeDetail detail(@PathVariable String idOrSlug) {
        return queryService.detail(idOrSlug);
    }

    @GetMapping("/{idOrSlug}/events")
    public List<EventSummary> events(@PathVariable String idOrSlug,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return queryService.events(idOrSlug, page, size);
    }
}
