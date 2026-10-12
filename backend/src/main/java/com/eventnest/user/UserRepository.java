package com.eventnest.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository - we write the interface, Spring generates the SQL
 * implementation at startup. This is not magic: {@code SimpleJpaRepository} uses
 * the method name to derive a query ({@code findByEmail} -> {@code WHERE email = ?}).
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    /**
     * Status-only lookup for the JWT filter. A projection rather than
     * {@code findById}: the filter needs one enum and must not pay for (or
     * accidentally serialise) the whole user row on every request.
     */
    @Query("select u.status from User u where u.id = :id")
    java.util.Optional<UserStatus> findStatusById(@Param("id") Long id);


    Optional<User> findByEmail(String email);

    /**
     * Used by the admin user table for the {@code q} search parameter.
     * {@code IgnoreCase} maps to {@code UPPER(...) LIKE UPPER(...)}.
     */
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmail(String email);

    long countByRole(UserRole role);

    long countByStatus(UserStatus status);

    /** Used by the "last active admin" guard in the suspension path. */
    long countByRoleAndStatus(UserRole role, UserStatus status);
}
