package com.eventnest.common.dto;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Our page envelope (decision D10).
 *
 * <p>We do not return Spring's {@code Page<T>} directly, for three concrete
 * reasons:
 * <ul>
 *   <li>{@code Page} serialises as a deeply nested object with internal state
 *       ({@code pageable}, {@code sort}, {@code pageable}) that leaks the
 *       persistence layer into our public API contract.</li>
 *   <li>It exposes {@code getTotalElements()}, which triggers an extra
 *       COUNT query on every list call even when the UI does not need a total.</li>
 *   <li>It carries {@code Entity} types. Mapping to DTOs here means the
 *       transaction closes before serialisation, which is what lets us run with
 *       {@code open-in-view: false}.</li>
 * </ul>
 *
 * @param content     the rows, already mapped to DTOs
 * @param page        0-based page index
 * @param totalPages  lets the UI render "Page 1 of 7" without dividing
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    public static <T> PageResponse<T> of(Page<?> page, List<T> content) {
        return new PageResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    /**
     * Maps entities to DTOs and builds the envelope in one step, so a controller
     * never forgets to convert (which is how lazy-loading bugs and accidental
     * password leaks happen).
     *
     * <p>The mapper must run while the transaction is open - the caller passes
     * entities already fetched inside the service transaction.
     */
    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return of(page, page.getContent().stream().map(mapper).toList());
    }

    public static <T> PageResponse<T> empty(int page, int size) {
        return new PageResponse<>(List.of(), page, size, 0, 0, true, true);
    }
}
