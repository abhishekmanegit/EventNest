package com.eventnest.admin;

import com.eventnest.admin.AdminDtos.AdminCollegeRow;
import com.eventnest.admin.AdminDtos.AdminCollegeResult;
import com.eventnest.admin.AdminDtos.CategoryRequest;
import com.eventnest.admin.AdminDtos.CollegeCreateRequest;
import com.eventnest.admin.AdminDtos.CollegeUpdateRequest;
import com.eventnest.category.CategoryDtos.CategoryResponse;
import com.eventnest.common.dto.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The college directory and the category taxonomy (ARCHITECTURE.md §7.5, D16).
 *
 * <p>Coleges and categories share a controller because they are the same kind of
 * content: database-driven reference data that the public API serves and only
 * an admin may change. Both are thin wrappers over a service that owns the
 * invariants (slug uniqueness, soft-vs-hard delete, verify semantics).
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCatalogController {

    private final AdminCatalogService adminCatalogService;

    public AdminCatalogController(AdminCatalogService adminCatalogService) {
        this.adminCatalogService = adminCatalogService;
    }

    // --------------------------------------------------------------- colleges --

    @GetMapping("/colleges")
    public PageResponse<AdminCollegeRow> colleges(@RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return adminCatalogService.colleges(page, Math.min(Math.max(1, size), 100));
    }

    @PostMapping("/colleges")
    public ResponseEntity<AdminCollegeResult> create(@Valid @RequestBody CollegeCreateRequest request) {
        AdminCollegeResult created = adminCatalogService.createCollege(
                request.name(), request.city(), request.state(), request.country(),
                request.websiteUrl(), request.about(), request.moderationRequired());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/colleges/{id}")
    public AdminCollegeResult update(@PathVariable Long id,
                                     @RequestBody CollegeUpdateRequest request) {
        return adminCatalogService.updateCollege(id, request.name(), request.city(),
                request.state(), request.country(), request.websiteUrl(), request.about(),
                request.moderationRequired(), request.verificationStatus());
    }

    // -------------------------------------------------------------- categories --

    /** All categories including inactive ones - see service javadoc for why. */
    @GetMapping("/categories")
    public java.util.List<CategoryResponse> categories() {
        return adminCatalogService.categories();
    }

    @PostMapping("/categories")
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminCatalogService.createCategory(request));
    }

    @PatchMapping("/categories/{id}")
    public CategoryResponse updateCategory(@PathVariable Long id,
                                           @RequestBody CategoryRequest request) {
        return adminCatalogService.updateCategory(id, request);
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        adminCatalogService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }
}