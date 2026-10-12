package com.eventnest.admin;

import com.eventnest.admin.AdminDtos.AdminCollegeRow;
import com.eventnest.admin.AdminDtos.CategoryRequest;
import com.eventnest.category.Category;
import com.eventnest.category.CategoryRepository;
import com.eventnest.college.College;
import com.eventnest.college.CollegeRepository;
import com.eventnest.college.CollegeVerificationStatus;
import com.eventnest.admin.AdminDtos.AdminCollegeResult;
import com.eventnest.common.dto.PageResponse;
import com.eventnest.common.exception.BadRequestException;
import com.eventnest.common.exception.ConflictException;
import com.eventnest.common.exception.ErrorCode;
import com.eventnest.common.exception.NotFoundException;
import com.eventnest.event.EventRepository;
import com.eventnest.event.EventStatus;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin CRUD over the two things an admin owns outright: the college directory
 * and the category taxonomy (ARCHITECTURE.md §7.5, D16).
 *
 * <p>Colleges are the load-bearing one. {@code verification_status} on a college
 * is what decides whether an organizer can publish at all, so this service is
 * the implementation of the trust gate at the institution level - the
 * account-level half of decision D19.
 */
@Service
@RequiredArgsConstructor
public class AdminCatalogService {

    private final CollegeRepository collegeRepository;
    private final CategoryRepository categoryRepository;
    private final EventRepository eventRepository;

    // ---------------------------------------------------------------- colleges --

    @Transactional(readOnly = true)
    public PageResponse<AdminCollegeRow> colleges(int page, int size) {
        Page<College> rows = collegeRepository.findAll(PageRequest.of(page, size));
        return PageResponse.of(rows, rows.getContent().stream()
                .map(college -> new AdminCollegeRow(
                        college.getId(),
                        college.getName(),
                        college.getSlug(),
                        college.getCity(),
                        college.getState(),
                        college.getVerificationStatus().name(),
                        college.isModerationRequired(),
                        eventRepository.countByCollegeId(college.getId()),
                        college.getCreatedAt()))
                .toList());
    }

    @Transactional
    public AdminCollegeResult createCollege(
            String name, String city, String state, String country,
            String websiteUrl, String about, Boolean moderationRequired) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("College name is required");
        }
        if (collegeRepository.existsByNameIgnoreCase(name.trim())) {
            throw new ConflictException(ErrorCode.DUPLICATE_RESOURCE,
                    "A college named “" + name.trim() + "” already exists");
        }
        College college = new College();
        apply(college, name, city, state, country, websiteUrl, about, moderationRequired);
        college.setVerificationStatus(CollegeVerificationStatus.PENDING);
        College saved = collegeRepository.save(college);
        return new AdminCollegeResult(saved.getId(), saved.getVerificationStatus().name());
    }

    /**
     * Partial update: only the non-null fields of the request are written. That
     * is the right semantics for PATCH, and it means an admin form that sends
     * back an untouched blank does not erase a value the college had set.
     */
    @Transactional
    public AdminCollegeResult updateCollege(Long id, String name, String city, String state,
                                            String country, String websiteUrl, String about,
                                            Boolean moderationRequired, String verificationStatus) {
        College college = collegeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("College not found: " + id));
        apply(college, name, city, state, country, websiteUrl, about, moderationRequired);
        if (verificationStatus != null && !verificationStatus.isBlank()) {
            college.setVerificationStatus(parseVerification(verificationStatus));
        }
        College saved = collegeRepository.save(college);
        return new AdminCollegeResult(saved.getId(), saved.getVerificationStatus().name());
    }

    private static void apply(College college, String name, String city, String state,
                              String country, String websiteUrl, String about,
                              Boolean moderationRequired) {
        if (name != null && !name.isBlank()) {
            college.setName(name.trim());
        }
        if (city != null) {
            college.setCity(trimToNull(city));
        }
        if (state != null) {
            college.setState(trimToNull(state));
        }
        if (country != null) {
            college.setCountry(trimToNull(country));
        }
        if (websiteUrl != null) {
            college.setWebsiteUrl(trimToNull(websiteUrl));
        }
        if (about != null) {
            college.setAbout(trimToNull(about));
        }
        if (moderationRequired != null) {
            college.setModerationRequired(moderationRequired);
        }
    }

    // -------------------------------------------------------------- categories --

    /**
     * Lists inactive categories too. The public endpoint filters them out; this
     * one must not, or an admin could neither re-activate nor delete a category
     * that had been turned off - a soft-delete you cannot undo.
     */
    @Transactional(readOnly = true)
    public List<com.eventnest.category.CategoryDtos.CategoryResponse> categories() {
        return categoryRepository.findAll(
                        PageRequest.of(0, 200,
                                org.springframework.data.domain.Sort.by("displayOrder", "name")))
                .getContent().stream()
                .map(com.eventnest.category.CategoryDtos.CategoryResponse::from)
                .toList();
    }

    @Transactional
    public com.eventnest.category.CategoryDtos.CategoryResponse createCategory(CategoryRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new BadRequestException("Category name is required");
        }
        String slug = request.slug() != null && !request.slug().isBlank()
                ? slugify(request.slug())
                : slugify(request.name());
        if (categoryRepository.findBySlug(slug).isPresent()) {
            throw new ConflictException(ErrorCode.DUPLICATE_RESOURCE,
                    "A category with slug “" + slug + "” already exists");
        }
        Category category = new Category();
        apply(category, slug, request);
        return com.eventnest.category.CategoryDtos.CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public com.eventnest.category.CategoryDtos.CategoryResponse updateCategory(
            Long id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Category not found: " + id));
        // The slug is deliberately immutable once created: it appears in URLs that
        // students have already bookmarked, and a category rename that silently
        // broke every link to it is the classic CMS foot-gun.
        apply(category, category.getSlug(), request);
        return com.eventnest.category.CategoryDtos.CategoryResponse.from(categoryRepository.save(category));
    }

    /**
     * Deactivating rather than deleting when the category has events, because a
     * hard delete would either fail on the foreign key or - with CASCADE - take
     * live events out of discovery along with it. A category with nothing
     * attached is safe to remove outright.
     */
    @Transactional
    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Category not found: " + id));
        if (eventRepository.countByCategoryIdAndStatusNotIn(id,
                List.of(EventStatus.DRAFT)) > 0) {
            category.setActive(false);
            categoryRepository.save(category);
            return;
        }
        categoryRepository.delete(category);
    }

    private static void apply(Category category, String slug, CategoryRequest request) {
        if (request.name() != null && !request.name().isBlank()) {
            category.setName(request.name().trim());
        }
        category.setSlug(slug);
        if (request.description() != null) {
            category.setDescription(trimToNull(request.description()));
        }
        if (request.icon() != null && !request.icon().isBlank()) {
            category.setIcon(request.icon().trim());
        }
        if (request.displayOrder() != null) {
            category.setDisplayOrder(request.displayOrder());
        }
        if (request.active() != null) {
            category.setActive(request.active());
        }
    }

    // ---------------------------------------------------------------- helpers --

    private static CollegeVerificationStatus parseVerification(String raw) {
        try {
            return CollegeVerificationStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unknown verification status: " + raw);
        }
    }

    /**
     * Same rule as the event slug: lowercase, hyphenated, ASCII only, so a
     * category created from a pasted name still produces a URL that survives a
     * copy-paste through email.
     */
    private static String slugify(String value) {
        String slug = value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return slug.isEmpty() ? "category" : slug;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
