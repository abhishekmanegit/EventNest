package com.eventnest.meta;

import com.eventnest.college.CollegeLocation;
import com.eventnest.college.CollegeRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/meta/locations} - the distinct state/city pairs that power
 * the location filter.
 *
 * <p>Precomputed into a lookup endpoint rather than derived client-side: the
 * alternative is shipping every college's address to the browser so it can
 * deduplicate them, and re-shipping the whole list whenever a college is added.
 * A single indexed query returning a few dozen rows is cheaper for everyone.
 */
@RestController
@RequestMapping("/api/v1/meta")
@RequiredArgsConstructor
public class MetaController {

    private final CollegeRepository collegeRepository;

    @GetMapping("/locations")
    public List<CollegeLocation> locations() {
        return collegeRepository.findDistinctLocations();
    }
}