package com.eventnest.organizer;

import com.eventnest.organizer.OrganizerProfileDtos.LogoUploadResponse;
import com.eventnest.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * College logo upload (ARCHITECTURE.md §7.4, {@code POST /organizer/college/logo}).
 *
 * <p>As with profile, the college is inferred from the caller's organizer row -
 * there is deliberately no way to ask the endpoint to write some other college's
 * logo.
 */
@RestController
@RequestMapping("/api/v1/organizer/college")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ORGANIZER')")
public class OrganizerCollegeController {

    private final OrganizerProfileService profileService;

    @PostMapping("/logo")
    public ResponseEntity<LogoUploadResponse> upload(
            @RequestParam("file") MultipartFile file) {
        LogoUploadResponse uploaded = profileService.uploadCollegeLogo(
                SecurityUtils.currentUserId(), file);
        return ResponseEntity.status(HttpStatus.CREATED).body(uploaded);
    }
}