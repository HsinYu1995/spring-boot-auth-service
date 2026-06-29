package com.authservice.token;

import com.authservice.user.Role;
import com.authservice.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class JwtServiceUnitTest {

    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() throws Exception {
        JwtProperties props = new JwtProperties();
        props.setPrivateKey(new ClassPathResource("keys/private.pem"));
        props.setPublicKey(new ClassPathResource("keys/public.pem"));
        props.setAccessTokenExpiration(900000L);
        props.setRefreshTokenExpiration(604800000L);
        jwtService = new JwtService(props);

        user = User.builder()
                .id(UUID.randomUUID())
                .email("unit-test@example.com")
                .password("encoded")
                .role(Role.ROLE_USER)
                .build();
    }

    @Test
    void generateAccessToken_returnsNonBlankToken() {
        String token = jwtService.generateAccessToken(user);
        assertThat(token).isNotNull().isNotBlank();
    }

    @Test
    void extractEmail_returnsCorrectEmail() {
        String token = jwtService.generateAccessToken(user);
        assertThat(jwtService.extractEmail(token)).isEqualTo("unit-test@example.com");
    }

    @Test
    void isTokenValid_validToken_returnsTrue() {
        String token = jwtService.generateAccessToken(user);
        assertThat(jwtService.isTokenValid(token, user)).isTrue();
    }

    @Test
    void isTokenValid_wrongUser_returnsFalse() {
        String token = jwtService.generateAccessToken(user);
        User other = User.builder().email("other@example.com").role(Role.ROLE_USER).build();
        assertThat(jwtService.isTokenValid(token, other)).isFalse();
    }

    @Test
    void isTokenValid_tamperedToken_returnsFalse() {
        String token = jwtService.generateAccessToken(user) + "tampered";
        assertThat(jwtService.isTokenValid(token, user)).isFalse();
    }

    @Test
    void getPublicKey_returnsNonNull() {
        assertThat(jwtService.getPublicKey()).isNotNull();
    }
}
