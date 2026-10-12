package com.eventnest.college;

import com.eventnest.college.CollegeDtos.CollegeCreateRequest;
import com.eventnest.college.CollegeDtos.CollegeOption;
import com.eventnest.common.exception.BadRequestException;
import com.eventnest.common.util.SlugUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The "my college isn't in the list" path on the public registration forms.
 *
 * <p>Married to {@link CollegeQueryService}: this is the write side of the same
 * directory. A student (or organizer) who cannot find their college types the
 * name; we create it as {@code PENDING} so it shows up in the dropdown for the
 * next person while staying out of the verified marketplace directory.
 *
 * <p>Idempotent by name on purpose. The whole point of persisting an ad-hoc
 * college is that the next typist finds it already there - "will it be in the
 * list next time" must not depend on anyone remembering the exact string the
 * first person invented.
 */
@Service
@RequiredArgsConstructor
public class CollegeCommandService {

    private static final int MAX_NAME_LENGTH = 180;

    private final CollegeRepository collegeRepository;

    @Transactional
    public CollegeOption create(CollegeCreateRequest request) {
        String name = request.name() == null ? null : request.name().trim();
        if (name == null || name.isBlank()) {
            throw new BadRequestException("College name is required.");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new BadRequestException("College name is too long.");
        }

        College existing = collegeRepository.findByNameIgnoreCase(name).orElse(null);
        if (existing != null) {
            return toOption(existing);
        }

        College college = new College();
        college.setName(name);
        college.setSlug(SlugUtil.uniqueSlug(name, collegeRepository::existsBySlug));
        // city/state are NOT NULL in the schema; store a blank rather than
        // re-architecting the directory for inputs that are typed by a student
        // who is also filling in their location two fields away.
        college.setCity(blankTo(request.city(), ""));
        college.setState(blankTo(request.state(), ""));
        college.setCountry(blankTo(request.country(), "India"));
        college.setVerificationStatus(CollegeVerificationStatus.PENDING);
        return toOption(collegeRepository.save(college));
    }

    private static CollegeOption toOption(College college) {
        return new CollegeOption(college.getId(), college.getName(), college.getSlug(), college.isVerified());
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}