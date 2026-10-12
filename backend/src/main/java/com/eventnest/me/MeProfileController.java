package com.eventnest.me;

import com.eventnest.security.SecurityUtils;
import com.eventnest.student.StudentProfileDtos.StudentProfileResponse;
import com.eventnest.student.StudentProfileDtos.UpdateStudentProfileRequest;
import com.eventnest.student.StudentProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The student's own profile (ARCHITECTURE.md §7, {@code GET/PUT /me/profile}).
 * The caller's identity comes from the token, never from the body or URL.
 */
@RestController
@RequestMapping("/api/v1/me/profile")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
public class MeProfileController {

    private final StudentProfileService profileService;

    @GetMapping
    public StudentProfileResponse get() {
        return profileService.get(SecurityUtils.currentUserId());
    }

    @PutMapping
    public StudentProfileResponse update(@Valid @RequestBody UpdateStudentProfileRequest request) {
        return profileService.update(SecurityUtils.currentUserId(), request);
    }
}