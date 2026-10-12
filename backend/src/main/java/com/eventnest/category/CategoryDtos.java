package com.eventnest.category;

/**
 * Category response payloads.
 *
 * <p>The {@code from} factory on the record keeps entity-to-DTO copying next to
 * the DTO's own shape. For a six-field projection with no nesting and no viewer
 * state, a MapStruct mapper would be strictly more code than the thing it
 * generates - the reason D2 exists is that entities must never reach the JSON
 * layer, not that every copy must be generated.
 */
public final class CategoryDtos {

    private CategoryDtos() {
    }

    public record CategoryResponse(
            Long id,
            String name,
            String slug,
            String description,
            String icon,
            String colorHex,
            int eventCount) {

        public static CategoryResponse from(Category category) {
            return new CategoryResponse(
                    category.getId(),
                    category.getName(),
                    category.getSlug(),
                    category.getDescription(),
                    category.getIcon(),
                    category.getColorHex(),
                    category.getEventCount());
        }
    }
}
