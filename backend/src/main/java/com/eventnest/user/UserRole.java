package com.eventnest.user;

/**
 * The three roles, matching the product model (ARCHITECTURE.md section 1):
 * students are the demand side, organizers are the supply side, admin governs.
 *
 * <p>Mapped as {@code EnumType.STRING} so the database stores
 * {@code 'STUDENT'} and not an ordinal. Storing an ordinal means reordering this
 * enum would silently reinterpret every existing row - a bug that is invisible
 * in tests and catastrophic in production.
 */
public enum UserRole {
    STUDENT,
    ORGANIZER,
    ADMIN;

    /** Spring Security authority name is always "ROLE_" + the enum name. */
    public String authority() {
        return "ROLE_" + name();
    }
}
