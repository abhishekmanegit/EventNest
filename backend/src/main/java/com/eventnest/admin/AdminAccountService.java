package com.eventnest.admin;

import com.eventnest.admin.AdminDtos.AdminOrganizerResult;
import com.eventnest.admin.AdminDtos.AdminOrganizerRow;
import com.eventnest.admin.AdminDtos.AdminUserResult;
import com.eventnest.admin.AdminDtos.AdminUserRow;
import com.eventnest.common.dto.PageResponse;
import com.eventnest.common.exception.BadRequestException;
import com.eventnest.common.exception.ConflictException;
import com.eventnest.common.exception.ErrorCode;
import com.eventnest.common.exception.NotFoundException;
import com.eventnest.notification.Notification;
import com.eventnest.notification.NotificationRepository;
import com.eventnest.notification.NotificationType;
import com.eventnest.organizer.Organizer;
import com.eventnest.organizer.OrganizerRepository;
import com.eventnest.organizer.OrganizerVerificationStatus;
import com.eventnest.security.RefreshTokenRepository;
import com.eventnest.security.SecurityUtils;
import com.eventnest.user.User;
import com.eventnest.user.UserRepository;
import com.eventnest.user.UserRole;
import com.eventnest.user.UserStatus;
import java.time.Clock;
import java.util.Objects;
import java.time.Instant;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The two account-level trust controls: suspending a user and approving the
 * colleges that get to publish (ARCHITECTURE.md §7.5, decision D19).
 *
 * <p>Both live together because they are the same operation in two shapes -
 * "change who this account is allowed to be on the platform" - and both share
 * the rule that the change is worthless unless it takes effect <b>now</b>. That
 * is why suspension revokes live refresh tokens in the same transaction as the
 * status flip: two writes that must agree, so they must be atomic.
 */
@Service
@RequiredArgsConstructor
public class AdminAccountService {

    private final UserRepository userRepository;
    private final OrganizerRepository organizerRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final NotificationRepository notificationRepository;
    private final Clock clock;

    // ------------------------------------------------------------------ users --

    @Transactional(readOnly = true)
    public PageResponse<AdminUserRow> users(String role, String status, String q, int page, int size) {
        Page<User> rows = userRepository.findAll(userSpec(role, status, q),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.from(rows, AdminAccountService::toUserRow);
    }

    /**
     * SUSPEND and ACTIVATE.
     *
     * <p>Three things happen in one transaction, and splitting them would be a
     * bug: flip the status, revoke every live refresh token, and write an audit
     * notification. If only the first happened, the account would be locked out
     * of new logins but an already-signed-in session would keep working for the
     * lifetime of its tokens - the exact failure that makes admins distrust the
     * suspend button.
     *
     * <p>An admin cannot suspend themselves. Not a hard requirement, but it is
     * the single easiest way to lock every admin out of a single-admin system,
     * and the guard costs one comparison.
     */
    @Transactional
    public AdminUserResult setStatus(Long userId, String statusValue, Long actorId) {
        UserStatus status = parseUserStatus(statusValue);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));

        if (Objects.equals(user.getId(), actorId) && status == UserStatus.SUSPENDED) {
            throw new ConflictException(ErrorCode.INVALID_STATE_TRANSITION,
                    "You cannot suspend your own account");
        }
        if (user.getRole() == UserRole.ADMIN && status == UserStatus.SUSPENDED
                && !Objects.equals(user.getId(), actorId)) {
            // Allowed - an org may legitimately need to disable a departed admin -
            // but never silently: this is the one status change that can reduce
            // the number of people who can undo it.
            if (userRepository.countByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE) <= 1) {
                throw new ConflictException(ErrorCode.INVALID_STATE_TRANSITION,
                        "Cannot suspend the last active admin");
            }
        }

        user.setStatus(status);
        if (status == UserStatus.SUSPENDED) {
            int revoked = refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now(clock));
            if (revoked > 0) {
                notify(user, "SYSTEM",
                        "Your account has been suspended",
                        "Contact support if you believe this is a mistake.");
            }
        }
        return new AdminUserResult(user.getId(), userRepository.save(user).getStatus().name());
    }

    // ------------------------------------------------------------- organizers --

    @Transactional(readOnly = true)
    public PageResponse<AdminOrganizerRow> organizers(String status, int page, int size) {
        OrganizerVerificationStatus parsed = parseOrganizerStatus(status);
        Page<Organizer> rows = parsed == null
                ? organizerRepository.findAllWithCollegeOrderByCreatedAtDesc(PageRequest.of(page, size))
                : organizerRepository.findByStatusWithCollegeOrderByCreatedAtDesc(
                        parsed, PageRequest.of(page, size));
        return PageResponse.from(rows, AdminAccountService::toOrganizerRow);
    }

    /**
     * The trust gate itself (decision D19). Approving an organizer is what
     * unlocks publishing for every event that college will ever create, so it is
     * the single most consequential mutation in the product - hence the
     * notification to the applicant, so an approval is never a thing you have to
     * discover by trying.
     */
    @Transactional
    public AdminOrganizerResult decide(Long organizerId, String action, String reason) {
        Organizer organizer = organizerRepository.findById(organizerId)
                .orElseThrow(() -> new NotFoundException("Organizer not found: " + organizerId));
        if (organizer.getVerificationStatus() == OrganizerVerificationStatus.APPROVED
                && "APPROVE".equalsIgnoreCase(action)) {
            return new AdminOrganizerResult(organizerId,
                    organizer.getVerificationStatus().name());
        }

        OrganizerVerificationStatus target = switch (action == null ? "" : action.trim().toUpperCase(Locale.ROOT)) {
            case "APPROVE" -> OrganizerVerificationStatus.APPROVED;
            case "REJECT" -> OrganizerVerificationStatus.REJECTED;
            default -> throw new BadRequestException("action must be APPROVE or REJECT");
        };

        // verified_by is not optional: ck_organizers_approval_audit refuses an
        // APPROVED row without one. The audit trail ("who let this college
        // publish?") is a hard database rule, not a javadoc convention.
        organizer.setVerifiedBy(SecurityUtils.currentUserId());
        organizer.setVerificationStatus(target);
        organizer.setVerifiedAt(Instant.now(clock));
        if (target == OrganizerVerificationStatus.REJECTED) {
            if (reason == null || reason.isBlank()) {
                throw new BadRequestException("A rejection must say why");
            }
            organizer.setRejectionReason(reason.trim());
        }
        Organizer saved = organizerRepository.save(organizer);

        notify(saved.getUser(),
                "APPROVAL_DECISION",
                target == OrganizerVerificationStatus.APPROVED
                        ? "Your organizer account has been approved"
                        : "Your organizer account was not approved",
                target == OrganizerVerificationStatus.APPROVED
                        ? "You can now publish events."
                        : organizer.getRejectionReason());

        return new AdminOrganizerResult(saved.getUserId(), saved.getVerificationStatus().name());
    }

    // ---------------------------------------------------------------- helpers --

    private static Specification<User> userSpec(String role, String status, String q) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (role != null && !role.isBlank()) {
                predicates.add(cb.equal(root.get("role"), parseRole(role)));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), parseUserStatus(status)));
            }
            if (q != null && !q.isBlank()) {
                String needle = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.<String>get("email")), needle),
                        cb.like(cb.lower(root.<String>get("fullName")), needle)));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private static UserRole parseRole(String raw) {
        try {
            return UserRole.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unknown role filter: " + raw);
        }
    }

    /**
     * Accepts both the API verb ({@code SUSPEND} / {@code ACTIVATE}) and the raw
     * enum name ({@code SUSPENDED} / {@code ACTIVE}). The API is a product
     * contract and uses verbs; the enum is a database literal and uses past
     * participles. Forcing the client to match the database's spelling would
     * leak schema naming into the API for no gain.
     */
    private static UserStatus parseUserStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BadRequestException("status is required");
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "SUSPEND", "SUSPENDED" -> UserStatus.SUSPENDED;
            case "ACTIVATE", "ACTIVE" -> UserStatus.ACTIVE;
            case "REJECT", "REJECTED" -> UserStatus.REJECTED;
            default -> throw new BadRequestException("Unknown status: " + raw);
        };
    }

    private static OrganizerVerificationStatus parseOrganizerStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OrganizerVerificationStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unknown status filter: " + raw);
        }
    }

    private void notify(User user, String type, String title, String body) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setType(NotificationType.valueOf(type));
        notification.setTitle(title);
        notification.setBody(body);
        notificationRepository.save(notification);
    }

    private static AdminUserRow toUserRow(User user) {
        return new AdminUserRow(user.getId(), user.getEmail(), user.getFullName(),
                user.getRole().name(), user.getStatus().name(), user.isEmailVerified(),
                user.getLastLoginAt(), user.getCreatedAt());
    }

    private static AdminOrganizerRow toOrganizerRow(Organizer organizer) {
        User user = organizer.getUser();
        return new AdminOrganizerRow(
                organizer.getUserId(),
                organizer.getUserId(),
                user == null ? null : user.getFullName(),
                user == null ? null : user.getEmail(),
                organizer.getDesignation(),
                organizer.getCollege() == null ? null : organizer.getCollege().getId(),
                organizer.getCollege() == null ? null : organizer.getCollege().getName(),
                organizer.getVerificationStatus().name(),
                organizer.getRejectionReason(),
                user == null ? null : user.getCreatedAt());
    }
}

