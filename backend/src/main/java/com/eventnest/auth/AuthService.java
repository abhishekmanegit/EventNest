package com.eventnest.auth;

import com.eventnest.auth.dto.AuthDtos.AuthResponse;
import com.eventnest.auth.dto.AuthDtos.OrganizerRegisterRequest;
import com.eventnest.auth.dto.AuthDtos.OrganizerSummary;
import com.eventnest.auth.dto.AuthDtos.StudentRegisterRequest;
import com.eventnest.auth.dto.AuthDtos.UserSummary;
import com.eventnest.college.College;
import com.eventnest.college.CollegeRepository;
import com.eventnest.common.exception.ConflictException;
import com.eventnest.common.exception.ErrorCode;
import com.eventnest.common.exception.NotFoundException;
import com.eventnest.common.exception.UnauthorizedException;
import com.eventnest.organizer.Organizer;
import com.eventnest.organizer.OrganizerRepository;
import com.eventnest.organizer.OrganizerVerificationStatus;
import com.eventnest.security.JwtService;
import com.eventnest.security.RefreshTokenService;
import com.eventnest.student.StudentProfile;
import com.eventnest.student.StudentProfileRepository;
import com.eventnest.user.User;
import com.eventnest.user.UserRepository;
import com.eventnest.user.UserRole;
import com.eventnest.user.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * All authentication and account-creation business logic.
 *
 * <p>The controller holds no logic at all - it validates shapes ({@code @Valid})
 * and delegates here. That separation is what makes the rules testable without
 * mocking the web layer.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final CollegeRepository collegeRepository;
    private final OrganizerRepository organizerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final Clock clock;

    // ================================================================ register

    @Transactional
    public AuthResponse registerStudent(StudentRegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException(ErrorCode.EMAIL_ALREADY_REGISTERED,
                    "An account with that email already exists.");
        }

        User user = new User();
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.STUDENT);
        user.setStatus(UserStatus.ACTIVE);
        user.setLastLoginAt(Instant.now(clock));
        userRepository.save(user);

        // 1:1 row created in the SAME transaction as the user. If this failed, the
        // user would be rolled back too - which is exactly what we want, rather
        // than a login with no profile.
        StudentProfile profile = new StudentProfile();
        profile.setUserId(user.getId());
        profile.setCourse(request.course());
        profile.setGraduationYear(request.graduationYear());
        profile.setPhone(request.phone());
        profile.setCity(request.city());
        profile.setDistrict(request.district());
        profile.setState(request.state());
        profile.setSkills(toSkillsArray(request.skills()));
        applyCollege(profile, request.collegeId());
        studentProfileRepository.save(profile);

        return buildAuthResponse(user, organizerRepository.findById(user.getId()).orElse(null));
    }

    /**
     * Creates an organizer account that is <strong>pending approval</strong>.
     *
     * <p>The account can log in immediately; only publishing is blocked. That is
     * decision D19 in action: trust is verified once per college, and the applicant
     * gets a clear in-app message rather than a dead end.
     */
    @Transactional
    public AuthResponse registerOrganizer(OrganizerRegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException(ErrorCode.EMAIL_ALREADY_REGISTERED,
                    "An account with that email already exists.");
        }

        College college = collegeRepository.findById(request.collegeId())
                .orElseThrow(() -> new NotFoundException("Select a valid college."));

        User user = new User();
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.ORGANIZER);
        user.setStatus(UserStatus.ACTIVE);
        user.setLastLoginAt(Instant.now(clock));
        userRepository.save(user);

        Organizer organizer = new Organizer();
        // Shared primary key: we assign the id the users insert just produced.
        // See the note on Organizer about why there is no @GeneratedValue here.
        organizer.setUserId(user.getId());
        organizer.setCollege(college);
        organizer.setDesignation(request.designation());
        organizer.setDepartment(request.department());
        organizer.setPhone(request.phone());
        organizer.setVerificationStatus(OrganizerVerificationStatus.PENDING);
        organizerRepository.save(organizer);

        log.info("Organizer application submitted for college '{}' by {}", college.getName(), email);
        return buildAuthResponse(user, organizer);
    }

    // ==================================================================== login

    @Transactional
    public AuthResponse login(String rawEmail, String rawPassword) {
        String email = normalizeEmail(rawEmail);

        Optional<User> found = userRepository.findByEmail(email);

        // Identical response for "no such email" and "wrong password", and we run
        // a dummy hash comparison in the missing-user case so the two paths take
        // comparable time. Without this, response timing alone tells an attacker
        // which addresses are registered.
        User user;
        if (found.isPresent()) {
            user = found.get();
            if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
                throw invalidCredentials();
            }
        } else {
            passwordEncoder.matches(rawPassword, "$2a$10$invalidinvalidinvalidinvalidinvalidinvalidinvalidinvalidin");
            throw invalidCredentials();
        }

        if (!user.getStatus().canAuthenticate()) {
            // Distinct message here is safe: the caller has already proven they
            // know the correct password.
            throw new UnauthorizedException(ErrorCode.ACCOUNT_SUSPENDED,
                    "This account has been suspended. Contact support@eventnest.dev.");
        }

        user.setLastLoginAt(Instant.now(clock));
        userRepository.save(user);

        return buildAuthResponse(user, organizerRepository.findById(user.getId()).orElse(null));
    }

    // ================================================================= refresh

    /**
     * Rotates the refresh token and re-checks the account on the way through.
     *
     * <p>Re-reading the user here is what closes the window described in
     * {@code EventNestUserDetailsService}: normal request auth trusts JWT claims
     * alone, so a freshly suspended user keeps access until their access token
     * expires. Going through refresh hits the database, so suspension takes effect
     * at the next refresh - at most 15 minutes away, usually seconds.
     */
    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        Long userId = refreshTokenService.rotate(rawRefreshToken)
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.INVALID_TOKEN,
                        "Your session has expired. Please sign in again."));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.INVALID_TOKEN,
                        "Your session has expired. Please sign in again."));

        if (!user.getStatus().canAuthenticate()) {
            refreshTokenService.revokeAllForUser(user.getId());
            throw new UnauthorizedException(ErrorCode.ACCOUNT_SUSPENDED,
                    "This account has been suspended.");
        }

        // rotate() already consumed and replaced the presented token, so we issue
        // the access token against a fresh refresh token below rather than
        // returning the (now revoked) incoming one.
        return buildAuthResponse(user, organizerRepository.findById(user.getId()).orElse(null));
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    // ================================================================ helpers

    @Transactional(readOnly = true)
    public UserSummary me(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Account not found."));
        return toUserSummary(user, organizerRepository.findById(userId).orElse(null));
    }

    /**
     * One place that mints the token pair, so every path (register, login,
     * refresh) issues identical credentials and we cannot forget the refresh token
     * on one branch.
     */
    private AuthResponse buildAuthResponse(User user, Organizer organizer) {
        JwtService.IssuedToken access = jwtService.issueAccessToken(
                user.getId(), user.getRole(), user.getFullName());
        String refresh = refreshTokenService.issue(user.getId());

        return new AuthResponse(
                access.value(),
                refresh,
                "Bearer",
                access.expiresInSeconds(),
                toUserSummary(user, organizer));
    }

    private UserSummary toUserSummary(User user, Organizer organizer) {
        return new UserSummary(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getAvatarUrl(),
                user.getRole(),
                user.getStatus().name(),
                organizer == null ? null : new OrganizerSummary(
                        organizer.getCollege().getId(),
                        organizer.getCollege().getName(),
                        organizer.getCollege().getSlug(),
                        organizer.getVerificationStatus().name(),
                        organizer.canPublish(),
                        organizer.getRejectionReason()));
    }

    /**
     * Lowercases and trims so {@code Rahul@X.com} and {@code rahul@x.com} are one
     * account. The UNIQUE index is on the stored value, so normalising on write is
     * what makes the constraint case-insensitive.
     */
    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private UnauthorizedException invalidCredentials() {
        return new UnauthorizedException(ErrorCode.INVALID_CREDENTIALS,
                "Email or password is incorrect.");
    }

    private void applyCollege(StudentProfile profile, Long collegeId) {
        if (collegeId == null) {
            return;
        }
        profile.setCollege(collegeRepository.findById(collegeId)
                .orElseThrow(() -> new NotFoundException("Select a valid college.")));
    }

    private String[] toSkillsArray(java.util.List<String> skills) {
        if (skills == null) {
            return new String[0];
        }
        return skills.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.trim())
                .distinct()
                .limit(15)
                .toArray(String[]::new);
    }
}