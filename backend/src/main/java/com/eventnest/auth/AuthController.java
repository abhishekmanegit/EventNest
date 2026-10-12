package com.eventnest.auth;

import com.eventnest.auth.dto.AuthDtos;
import com.eventnest.auth.dto.AuthDtos.AuthResponse;
import com.eventnest.auth.dto.AuthDtos.OrganizerRegisterRequest;
import com.eventnest.auth.dto.AuthDtos.StudentRegisterRequest;
import com.eventnest.auth.dto.AuthDtos.UserSummary;
import com.eventnest.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP surface for authentication.
 *
 * <p>Deliberately contains no business logic. Its only jobs are: accept JSON,
 * run Bean Validation ({@code @Valid} - which is what produces the 400 +
 * fieldErrors the frontend renders inline), delegate, and choose a status code.
 *
 * <p>Note that login takes its credentials as two {@code @RequestParam}s rather
 * than a JSON body. That is the OAuth 2.0 convention
 * ({@code application/x-www-form-urlencoded}), which is why almost every auth
 * tutorial does it that way - but for a first-party SPA a JSON body is easier to
 * call from JavaScript and lets us return proper 400 validation errors. This is
 * one of those cases where following the convention would make our own frontend
 * worse, so we deliberately do not.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register/student")
    public ResponseEntity<AuthResponse> registerStudent(
            @Valid @RequestBody StudentRegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registerStudent(request));
    }

    @PostMapping("/register/organizer")
    public ResponseEntity<AuthResponse> registerOrganizer(
            @Valid @RequestBody OrganizerRegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registerOrganizer(request));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return authService.login(request.email(), request.password());
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody AuthDtos.RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    /**
     * Always {@code 204}, even with no token supplied. Logout must not be able to
     * fail in a way that tells an attacker whether a token was valid.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) AuthDtos.LogoutRequest request) {
        authService.logout(request == null ? null : request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    /**
     * Who am I? Called on every page load to rehydrate the session, which is why it
     * returns the full summary including organizer approval state.
     */
    @GetMapping("/me")
    public UserSummary me() {
        return authService.me(SecurityUtils.currentUserId());
    }
}