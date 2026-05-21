package com.authservice.auth;

import com.authservice.AbstractIntegrationTest;
import com.authservice.auth.dto.AuthResponse;
import com.authservice.auth.dto.ForgotPasswordRequest;
import com.authservice.auth.dto.LoginRequest;
import com.authservice.auth.dto.RegisterRequest;
import com.authservice.token.PasswordResetTokenRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("Layer1")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class AuthControllerL1Test extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Test
    void register_returnsCreated_withTokensAndRefreshCookie() {
        RegisterRequest request = new RegisterRequest("newuser-l1@example.com", "password123");
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                baseUrl() + "/auth/register", request, AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().accessToken()).isNotBlank();
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).anyMatch(value -> value.contains("refresh_token="));
    }

    @Test
    void login_withValidCredentials_returnsTokens_andAllowsMeEndpoint() {
        String email = "meuser-l1@example.com";
        String password = "password123";
        registerUser(email, password);

        LoginRequest loginRequest = new LoginRequest(email, password);
        ResponseEntity<AuthResponse> loginResponse = restTemplate.postForEntity(
                baseUrl() + "/auth/login", loginRequest, AuthResponse.class);

        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResponse.getBody()).isNotNull();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(loginResponse.getBody().accessToken());
        ResponseEntity<Map<String, Object>> meResponse = restTemplate.exchange(
                baseUrl() + "/auth/me", HttpMethod.GET, new HttpEntity<>(headers), new org.springframework.core.ParameterizedTypeReference<>() {
                });

        assertThat(meResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(meResponse.getBody()).containsEntry("email", email);
    }

    @Test
    void refresh_withRefreshCookie_returnsNewAccessToken() {
        String email = "refreshuser-l1@example.com";
        String password = "password123";
        ResponseEntity<AuthResponse> registerResponse = restTemplate.postForEntity(
                baseUrl() + "/auth/register", new RegisterRequest(email, password), AuthResponse.class);

        String refreshCookie = extractRefreshCookie(registerResponse.getHeaders().get(HttpHeaders.SET_COOKIE));
        assertThat(refreshCookie).isNotBlank();

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, refreshCookie);

        ResponseEntity<AuthResponse> refreshResponse = restTemplate.exchange(
                baseUrl() + "/auth/refresh",
                HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(headers),
                AuthResponse.class);

        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refreshResponse.getBody()).isNotNull();
        assertThat(refreshResponse.getBody().accessToken()).isNotBlank();
        assertThat(refreshResponse.getHeaders().get(HttpHeaders.SET_COOKIE)).anyMatch(value -> value.contains("refresh_token="));
    }

    @Test
    void logout_revokesRefreshToken() {
        String email = "logoutuser-l1@example.com";
        String password = "password123";
        ResponseEntity<AuthResponse> registerResponse = restTemplate.postForEntity(
                baseUrl() + "/auth/register", new RegisterRequest(email, password), AuthResponse.class);

        String refreshCookie = extractRefreshCookie(registerResponse.getHeaders().get(HttpHeaders.SET_COOKIE));
        assertThat(refreshCookie).isNotBlank();

        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(registerResponse.getBody().accessToken());
        ResponseEntity<Void> logoutResponse = restTemplate.exchange(
                baseUrl() + "/auth/logout",
                HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(authHeaders),
                Void.class);

        assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        HttpHeaders refreshHeaders = new HttpHeaders();
        refreshHeaders.add(HttpHeaders.COOKIE, refreshCookie);
        ResponseEntity<String> refreshAfterLogout = restTemplate.exchange(
                baseUrl() + "/auth/refresh",
                HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(refreshHeaders),
                String.class);

        assertThat(refreshAfterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void forgotPassword_createsResetToken() {
        String email = "forgotuser-l1@example.com";
        String password = "password123";
        registerUser(email, password);

        ResponseEntity<Void> response = restTemplate.postForEntity(
                baseUrl() + "/auth/forgot-password",
                new ForgotPasswordRequest(email),
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(passwordResetTokenRepository.findAll())
                .anyMatch(token -> token.getUser().getEmail().equals(email));
    }

    private void registerUser(String email, String password) {
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                baseUrl() + "/auth/register", new RegisterRequest(email, password), AuthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private String extractRefreshCookie(List<String> setCookieHeaders) {
        assertThat(setCookieHeaders).isNotEmpty();
        return setCookieHeaders.stream()
                .map(cookie -> cookie.split(";", 2)[0])
                .filter(cookie -> cookie.startsWith("refresh_token="))
                .findFirst()
                .orElseThrow();
    }
}
