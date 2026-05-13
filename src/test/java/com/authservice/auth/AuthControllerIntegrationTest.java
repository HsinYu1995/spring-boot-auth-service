package com.authservice.auth;

import com.authservice.AbstractIntegrationTest;
import com.authservice.auth.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class AuthControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void register_returnsCreatedWithTokens() {
        RegisterRequest request = new RegisterRequest("newuser@example.com", "password123");
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                baseUrl() + "/auth/register", request, AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().accessToken()).isNotBlank();
        assertThat(response.getBody().refreshToken()).isNotBlank();
    }

    @Test
    void register_withDuplicateEmail_returnsConflict() {
        RegisterRequest request = new RegisterRequest("duplicate@example.com", "password123");
        restTemplate.postForEntity(baseUrl() + "/auth/register", request, AuthResponse.class);

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/auth/register", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void login_withValidCredentials_returnsTokens() {
        RegisterRequest registerRequest = new RegisterRequest("loginuser@example.com", "password123");
        restTemplate.postForEntity(baseUrl() + "/auth/register", registerRequest, AuthResponse.class);

        LoginRequest loginRequest = new LoginRequest("loginuser@example.com", "password123");
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                baseUrl() + "/auth/login", loginRequest, AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().accessToken()).isNotBlank();
    }

    @Test
    void login_withInvalidCredentials_returnsUnauthorized() {
        LoginRequest loginRequest = new LoginRequest("nonexistent@example.com", "wrongpassword");
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/auth/login", loginRequest, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refresh_withValidToken_returnsNewTokens() {
        RegisterRequest registerRequest = new RegisterRequest("refreshuser@example.com", "password123");
        AuthResponse authResponse = restTemplate.postForEntity(
                baseUrl() + "/auth/register", registerRequest, AuthResponse.class).getBody();

        assertThat(authResponse).isNotNull();
        RefreshTokenRequest refreshRequest = new RefreshTokenRequest(authResponse.refreshToken());
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                baseUrl() + "/auth/refresh", refreshRequest, AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().accessToken()).isNotBlank();
        assertThat(response.getBody().refreshToken()).isNotBlank();
    }

    @Test
    void register_withInvalidEmail_returnsBadRequest() {
        RegisterRequest request = new RegisterRequest("not-an-email", "password123");
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/auth/register", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
