package com.authservice.token;

import com.authservice.user.Role;
import com.authservice.user.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TokenEntityTest {

    private User buildUser() {
        return User.builder().email("u@example.com").password("pw").role(Role.ROLE_USER).build();
    }

    // RefreshToken

    @Test
    void refreshToken_isExpired_returnsTrueWhenPast() {
        RefreshToken token = RefreshToken.builder()
                .tokenHash("hash")
                .user(buildUser())
                .expiresAt(LocalDateTime.now().minusSeconds(1))
                .build();
        assertThat(token.isExpired()).isTrue();
    }

    @Test
    void refreshToken_isExpired_returnsFalseWhenFuture() {
        RefreshToken token = RefreshToken.builder()
                .tokenHash("hash")
                .user(buildUser())
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        assertThat(token.isExpired()).isFalse();
    }

    @Test
    void refreshToken_defaultNotRevoked() {
        RefreshToken token = RefreshToken.builder()
                .tokenHash("hash")
                .user(buildUser())
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        assertThat(token.isRevoked()).isFalse();
    }

    // PasswordResetToken

    @Test
    void passwordResetToken_isExpired_returnsTrueWhenPast() {
        PasswordResetToken token = PasswordResetToken.builder()
                .tokenHash("hash")
                .user(buildUser())
                .expiresAt(LocalDateTime.now().minusSeconds(1))
                .build();
        assertThat(token.isExpired()).isTrue();
    }

    @Test
    void passwordResetToken_isExpired_returnsFalseWhenFuture() {
        PasswordResetToken token = PasswordResetToken.builder()
                .tokenHash("hash")
                .user(buildUser())
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        assertThat(token.isExpired()).isFalse();
    }

    @Test
    void passwordResetToken_defaultNotUsed() {
        PasswordResetToken token = PasswordResetToken.builder()
                .tokenHash("hash")
                .user(buildUser())
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        assertThat(token.isUsed()).isFalse();
    }
}
