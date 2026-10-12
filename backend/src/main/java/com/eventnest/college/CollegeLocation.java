package com.eventnest.college;

/**
 * A distinct {@code state}/{@code city} pair from the college directory - the
 * data behind the location filter.
 *
 * <p>A dedicated type rather than {@code Object[]} or a {@code Map}: a projection
 * record is what the controller returns, it is immune to column-order mistakes
 * (the classic bug with positional projections), and the JSON shape is obvious
 * from the declaration.
 *
 * <p>Declared top-level so JPQL constructor expressions can reference it by its
 * fully qualified name - Hibernate's {@code SELECT new ...} syntax cannot reliably
 * resolve a nested type.
 */
public record CollegeLocation(String state, String city) {
}
