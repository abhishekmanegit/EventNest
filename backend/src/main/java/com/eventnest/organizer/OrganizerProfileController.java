package com.eventnest.organizer;

import com.eventnest.organizer.OrganizerProfileDtos.OrganizerProfileResponse;
import com.eventnest.organizer.OrganizerProfileDtos.UpdateOrganizerProfileRequest;
import com.eventnest.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Organizer profile surface (ARCHITECTURE.md §7.4, {@code /organizer/profile}).
 *
 * <p>No id in the path: the caller's identity comes from the token, never from
 * a URL the organizer could point at someone else's profile.
 */
@RestController
@RequestMapping("/api/v1/organizer/profile")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ORGANIZER')")
public class OrganizerProfileController {

    private final OrganizerProfileService profileService;

    @GetMapping
    public OrganizerProfileResponse get() {
        return profileService.get(SecurityUtils.currentUserId());
    }

    @PutMapping
    public OrganizerProfileResponse update(@Valid @RequestBody UpdateOrganizerProfileRequest request) {
        return profileService.update(SecurityUtils.currentUserId(), request);
    }
}