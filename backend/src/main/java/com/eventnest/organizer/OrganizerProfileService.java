package com.eventnest.organizer;

import com.eventnest.college.College;
import com.eventnest.college.CollegeRepository;
import com.eventnest.common.exception.NotFoundException;
import com.eventnest.organizer.OrganizerProfileDtos.LogoUploadResponse;
import com.eventnest.organizer.OrganizerProfileDtos.OrganizerProfileResponse;
import com.eventnest.organizer.OrganizerProfileDtos.UpdateOrganizerProfileRequest;
import com.eventnest.storage.LocalFileStorageService;
import com.eventnest.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * The organizer profile: who they are (name/designation/phone) and the college
 * they publish for. Read is one round trip; edits land on the same rows a
 * register created, so re-logging-in shows the same face.
 */
@Service
@RequiredArgsConstructor
public class OrganizerProfileService {

    private static final String LOGO_SUBDIR = "college-logos";

    private final OrganizerRepository organizerRepository;
    private final CollegeRepository collegeRepository;
    private final LocalFileStorageService fileStorage;

    @Transactional(readOnly = true)
    public OrganizerProfileResponse get(Long userId) {
        return toResponse(requireOrganizer(userId));
    }

    @Transactional
    public OrganizerProfileResponse update(Long userId, UpdateOrganizerProfileRequest request) {
        Organizer organizer = requireOrganizer(userId);
        if (request.fullName() != null) {
            String name = request.fullName().trim();
            if (name.isBlank()) {
                throw new IllegalArgumentException("Name cannot be blank.");
            }
            organizer.getUser().setFullName(name);
        }
        organizer.setDesignation(blankToNull(request.designation()));
        organizer.setDepartment(blankToNull(request.department()));
        organizer.setPhone(blankToNull(request.phone()));
        return toResponse(organizer);
    }

    /**
     * The logo belongs to the <b>college</b>, not to the organizer - a society
     * lead scrolling it once should not have to re-up it after the officer
     * rotates. Two approved organizers of the same college share a logo, which is
     * why the write goes to {@code colleges.logo_url} and not to a per-organizer
     * column.
     */
    @Transactional
    public LogoUploadResponse uploadCollegeLogo(Long userId, MultipartFile file) {
        Organizer organizer = requireOrganizer(userId);
        String url = fileStorage.store(file, LOGO_SUBDIR);
        College college = organizer.getCollege();
        college.setLogoUrl(url);
        collegeRepository.save(college);
        return new LogoUploadResponse(url);
    }

    private Organizer requireOrganizer(Long userId) {
        return organizerRepository.findByUserIdWithCollege(userId)
                .orElseThrow(() -> new NotFoundException("Organizer profile not found."));
    }

    private static OrganizerProfileResponse toResponse(Organizer organizer) {
        User user = organizer.getUser();
        College college = organizer.getCollege();
        return new OrganizerProfileResponse(
                organizer.getUserId(),
                user.getFullName(),
                user.getEmail(),
                user.getAvatarUrl(),
                organizer.getDesignation(),
                organizer.getDepartment(),
                organizer.getPhone(),
                college.getId(),
                college.getName(),
                college.getSlug(),
                college.getLogoUrl(),
                college.getCity(),
                college.getState(),
                organizer.getVerificationStatus().name(),
                organizer.canPublish(),
                organizer.getRejectionReason());
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}