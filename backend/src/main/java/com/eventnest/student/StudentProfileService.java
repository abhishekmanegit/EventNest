package com.eventnest.student;

import com.eventnest.college.College;
import com.eventnest.college.CollegeRepository;
import com.eventnest.common.exception.BadRequestException;
import com.eventnest.common.exception.NotFoundException;
import com.eventnest.student.StudentProfileDtos.StudentProfileResponse;
import com.eventnest.student.StudentProfileDtos.UpdateStudentProfileRequest;
import com.eventnest.user.User;
import com.eventnest.user.UserRepository;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The student's self-editable profile (ARCHITECTURE.md §7, {@code GET/PUT /me/profile}).
 *
 * <p>City/state live here because "Near You" on the home rails is driven by
 * them - a student who never fills the profile quietly gets location-less rails
 * rather than a crash.
 */
@Service
@RequiredArgsConstructor
public class StudentProfileService {

    private final StudentProfileRepository profileRepository;
    private final CollegeRepository collegeRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public StudentProfileResponse get(Long userId) {
        return toResponse(requireUser(userId), requireProfile(userId));
    }

    /**
     * Full replace of the editable fields. {@code graduationYear} bounds checked
     * here rather than by a validation annotation because upstream callers may
     * pass it through other layers; this one line is the single gate.
     */
    @Transactional
    public StudentProfileResponse update(Long userId, UpdateStudentProfileRequest request) {
        User user = requireUser(userId);
        StudentProfile profile = requireProfile(userId);

        if (request.fullName() != null) {
            String name = request.fullName().trim();
            if (name.isBlank()) {
                throw new BadRequestException("Name cannot be blank.");
            }
            user.setFullName(name);
        }
        if (request.collegeId() != null) {
            profile.setCollege(collegeRepository.findById(request.collegeId())
                    .orElseThrow(() -> new BadRequestException("Unknown college.")));
        }
        profile.setCourse(trim(request.course()));
        if (request.graduationYear() != null) {
            if (request.graduationYear() < 1990 || request.graduationYear() > 2100) {
                throw new BadRequestException("Graduation year is out of range.");
            }
            profile.setGraduationYear(request.graduationYear());
        }
        profile.setPhone(trim(request.phone()));
        profile.setCity(trim(request.city()));
        profile.setDistrict(trim(request.district()));
        profile.setState(trim(request.state()));
        profile.setSkills(normalizeSkills(request.skills()));

        profileRepository.save(profile);
        return toResponse(user, profile);
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found."));
    }

    private StudentProfile requireProfile(Long userId) {
        return profileRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(
                        "Profile not found. Register as a student first."));
    }

    private static StudentProfileResponse toResponse(User user, StudentProfile profile) {
        College college = profile.getCollege();
        String collegeName = college == null ? null : college.getName();
        String collegeSlug = college == null ? null : college.getSlug();
        Long collegeId = college == null ? null : college.getId();
        return new StudentProfileResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getAvatarUrl(),
                collegeId,
                collegeName,
                collegeSlug,
                profile.getCourse(),
                profile.getGraduationYear(),
                profile.getPhone(),
                profile.getCity(),
                profile.getDistrict(),
                profile.getState(),
                profile.skillList());
    }

    private static String[] normalizeSkills(List<String> raw) {
        if (raw == null) {
            return new String[0];
        }
        LinkedHashSet<String> skills = new LinkedHashSet<>();
        for (String skill : raw) {
            if (skill != null && !skill.isBlank()) {
                skills.add(skill.trim());
            }
        }
        return skills.toArray(new String[0]);
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}