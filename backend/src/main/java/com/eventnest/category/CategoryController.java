package com.eventnest.category;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The taxonomy the home page grid, the filter chips and the create-event form
 * all read (decision D16: database-driven, so an admin can add a category
 * without a deploy).
 *
 * <p>Returns the cached denormalised {@code event_count} rather than counting
 * events here. Refreshing it is one function call from the places that actually
 * change the number; counting live would be fifteen {@code COUNT} queries on
 * every page load to save a value that only moves when an event is published.
 */
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryRepository categoryRepository;

    @GetMapping
    public List<CategoryDtos.CategoryResponse> list() {
        return categoryRepository.findByActiveTrueOrderByDisplayOrderAscNameAsc().stream()
                .map(CategoryDtos.CategoryResponse::from)
                .toList();
    }
}
