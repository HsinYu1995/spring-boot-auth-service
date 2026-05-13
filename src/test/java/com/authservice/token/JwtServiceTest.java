package com.authservice.token;

import com.authservice.user.Role;
import com.authservice.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class JwtServiceTest {

    @Autowired
    private JwtService jwtService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .password("encoded-password")
                .role(Role.ROLE_USER)
                .build();
    }

    @Test
    void generateAccessToken_returnsNonNullToken() {
        String token = jwtService.generateAccessToken(user);
        assertThat(token).isNotNull().isNotBlank();
    }

    @Test
    void extractEmail_returnsCorrectEmail() {
        String token = jwtService.generateAccessToken(user);
        assertThat(jwtService.extractEmail(token)).isEqualTo(user.getEmail());
    }

    @Test
    void isTokenValid_returnsTrueForValidToken() {
        String token = jwtService.generateAccessToken(user);
        assertThat(jwtService.isTokenValid(token, user)).isTrue();
    }

    @Test
    void isTokenValid_returnsFalseForWrongUser() {
        String token = jwtService.generateAccessToken(user);
        User otherUser = User.builder()
                .id(UUID.randomUUID())
                .email("other@example.com")
                .role(Role.ROLE_USER)
                .build();
        assertThat(jwtService.isTokenValid(token, otherUser)).isFalse();
    }

    @Test
    void isTokenValid_returnsFalseForTamperedToken() {
        String token = jwtService.generateAccessToken(user) + "tampered";
        assertThat(jwtService.isTokenValid(token, user)).isFalse();
    }
}
