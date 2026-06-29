package com.authservice.auth;

import com.authservice.auth.dto.*;
import com.authservice.token.JwtProperties;
import com.authservice.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Auth endpoints")
public class AuthController {

    private static final String REFRESH_COOKIE = "refresh_token";

    private final AuthService authService;
    private final JwtProperties jwtProperties;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new user")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request, HttpServletResponse response) {
        AuthTokenPair pair = authService.register(request);
        setRefreshCookie(response, pair.refreshToken(), pair.expiresIn());
        return AuthResponse.of(pair.accessToken(), pair.expiresIn());
    }

    @PostMapping("/login")
    @Operation(summary = "Login and receive tokens")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthTokenPair pair = authService.login(request);
        setRefreshCookie(response, pair.refreshToken(), pair.expiresIn());
        return AuthResponse.of(pair.accessToken(), pair.expiresIn());
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token")
    public AuthResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        String rawToken = extractRefreshCookie(request);
        AuthTokenPair pair = authService.refresh(rawToken);
        setRefreshCookie(response, pair.refreshToken(), pair.expiresIn());
        return AuthResponse.of(pair.accessToken(), pair.expiresIn());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Logout and revoke refresh tokens", security = @SecurityRequirement(name = "bearerAuth"))
    public void logout(@AuthenticationPrincipal User user, HttpServletResponse response) {
        authService.logout(user);
        clearRefreshCookie(response);
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user info", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Map<String, Object>> me(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "email", user.getEmail(),
                "role", user.getRole(),
                "createdAt", user.getCreatedAt()
        ));
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Request a password reset email")
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Reset password using token from email")
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
    }

    private void setRefreshCookie(HttpServletResponse response, String token, long accessTokenExpiresIn) {
        long refreshMaxAge = jwtProperties.getRefreshTokenExpiration() / 1000;
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, token)
                .httpOnly(true)
                .secure(false) // set to true in production (HTTPS)
                .path("/api/v1/auth")
                .maxAge(refreshMaxAge)
                .sameSite("Strict")
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(false)
                .path("/api/v1/auth")
                .maxAge(0)
                .sameSite("Strict")
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private String extractRefreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token missing");
        }
        return Arrays.stream(request.getCookies())
                .filter(c -> REFRESH_COOKIE.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token missing"));
    }
}
