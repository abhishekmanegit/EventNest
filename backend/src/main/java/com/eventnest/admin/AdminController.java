package com.eventnest.admin;

import com.eventnest.admin.AdminDtos.AdminOrganizerResult;
import com.eventnest.admin.AdminDtos.AdminOrganizerRow;
import com.eventnest.admin.AdminDtos.AdminUserResult;
import com.eventnest.admin.AdminDtos.AdminUserRow;
import com.eventnest.admin.AdminDtos.OrganizerApprovalRequest;
import com.eventnest.admin.AdminDtos.PlatformStats;
import com.eventnest.admin.AdminDtos.SetUserStatusRequest;
import com.eventnest.common.dto.PageResponse;
import com.eventnest.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stats, users and the organizer approval queue (ARCHITECTURE.md §7.5).
 *
 * <p>A single controller for the account-and-government surface, because they
 * share one {@code hasRole('ADMIN')} gate and one service. The event/report
 * queues live in {@link AdminModerationController}; colleges and categories in
 * {@link AdminCatalogController}. Three controllers is not fragmentation - it is
 * the same grouping as the admin nav sidebar.
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final PlatformStatsService platformStatsService;
    private final AdminAccountService adminAccountService;

    public AdminController(PlatformStatsService platformStatsService,
                           AdminAccountService adminAccountService) {
        this.platformStatsService = platformStatsService;
        this.adminAccountService = adminAccountService;
    }

    @GetMapping("/stats")
    public PlatformStats stats() {
        return platformStatsService.stats();
    }

    @GetMapping("/users")
    public PageResponse<AdminUserRow> users(@RequestParam(required = false) String role,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false) String q,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return adminAccountService.users(role, status, q, page, clamp(size));
    }

    /**
     * SUSPEND / ACTIVATE. The response is the row's new status, not a message,
     * so the admin table can re-render in place without re-reading the list.
     */
    @PatchMapping("/users/{id}/status")
    public AdminUserResult setUserStatus(@PathVariable Long id,
                                         @Valid @RequestBody SetUserStatusRequest request) {
        return adminAccountService.setStatus(id, request.status(), SecurityUtils.currentUserId());
    }

    @GetMapping("/organizers")
    public PageResponse<AdminOrganizerRow> organizers(@RequestParam(required = false) String status,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return adminAccountService.organizers(status, page, clamp(size));
    }

    /** APPROVE / REJECT - the single trust decision behind decision D19. */
    @PatchMapping("/organizers/{id}/approval")
    public AdminOrganizerResult approve(@PathVariable Long id,
                                        @Valid @RequestBody OrganizerApprovalRequest request) {
        return adminAccountService.decide(id, request.action(), request.reason());
    }

    private static int clamp(int size) {
        return Math.min(Math.max(1, size), 100);
    }
}