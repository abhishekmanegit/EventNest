package com.eventnest.security;

import com.eventnest.user.User;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The authenticated principal.
 *
 * <p>We implement Spring Security's {@link UserDetails} rather than passing the
 * JPA {@code User} entity around, because:
 * <ul>
 *   <li>the framework's contracts ({@code Authentication}, {@code @AuthenticationPrincipal})
 *       are typed to {@code UserDetails};</li>
 *   <li>keeping the entity out of the security package means a future refactor of
 *       {@code users} cannot silently break authentication;</li>
 *   <li>the password hash is available only where it is needed - and note that
 *       even here we return the empty string, because we authenticate against our
 *       own {@code refresh_tokens} table, never through the
 *       {@code DaoAuthenticationProvider} password path.</li>
 * </ul>
 */
public record EventNestUserPrincipal(
        Long id,
        String email,
        String fullName,
        String role,
        boolean active,
        Collection<? extends GrantedAuthority> authorities) implements UserDetails {

    public static EventNestUserPrincipal from(User user) {
        List<GrantedAuthority> granted = List.of(
                new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        return new EventNestUserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name(),
                user.getStatus().canAuthenticate(),
                granted);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    /** Spring Security's UserDetails method. Not the password hash. */
    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}