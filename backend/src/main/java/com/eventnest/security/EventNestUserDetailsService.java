package com.eventnest.security;

import com.eventnest.user.User;
import com.eventnest.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads a user for authentication.
 *
 * <p>Only used where a database round-trip is genuinely needed - notably the
 * refresh path, where we re-read the account to confirm it has not been suspended
 * since the access token was issued. Normal request authentication uses the JWT
 * claims alone and never touches this, which is what keeps the API fast: no
 * database hit per authenticated request.
 *
 * <p>That trade has a known consequence worth naming: suspending a user does not
 * invalidate their existing access token until it expires (<= 15 minutes). For a
 * 15-minute window that is an acceptable price for removing a database read from
 * every request. The refresh path closes the window immediately.
 */
@Service
@RequiredArgsConstructor
public class EventNestUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public EventNestUserPrincipal loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("No account for that email"));
        return EventNestUserPrincipal.from(user);
    }

    @Transactional(readOnly = true)
    public EventNestUserPrincipal loadById(Long userId) {
        return userRepository.findById(userId)
                .map(EventNestUserPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("No account with id " + userId));
    }
}