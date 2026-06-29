package com.authservice.auth.dto;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
    public static AuthResponse of(String accessToken, long expiresIn) {
        return new AuthResponse(accessToken, "Bearer", expiresIn);
    }
}
