package com.authservice.auth;

import com.authservice.auth.dto.*;
import com.authservice.exception.GlobalExceptionHandler;
import com.authservice.token.JwtProperties;
import com.authservice.user.Role;
import com.authservice.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock private AuthService authService;
    @Mock private JwtProperties jwtProperties;
    @InjectMocks private AuthController controller;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private User user;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
        user = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .password("encoded")
                .role(Role.ROLE_USER)
                .createdAt(LocalDateTime.now())
                .build();
        lenient().when(jwtProperties.getRefreshTokenExpiration()).thenReturn(604800000L);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void register_success_returnsCreatedWithAccessToken() throws Exception {
        AuthTokenPair pair = new AuthTokenPair("accessToken", "refreshToken", 900000L);
        when(authService.register(any(RegisterRequest.class))).thenReturn(pair);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest("test@example.com", "password123"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("accessToken"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void login_success_returnsOkWithAccessToken() throws Exception {
        AuthTokenPair pair = new AuthTokenPair("accessToken", "refreshToken", 900000L);
        when(authService.login(any(LoginRequest.class))).thenReturn(pair);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("test@example.com", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("accessToken"));
    }

    @Test
    void refresh_noCookie_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_withValidCookie_returnsNewAccessToken() throws Exception {
        AuthTokenPair pair = new AuthTokenPair("newAccessToken", "newRefreshToken", 900000L);
        when(authService.refresh("oldToken")).thenReturn(pair);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", "oldToken")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("newAccessToken"));
    }

    @Test
    void logout_withAuthenticatedUser_returnsNoContent() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));

        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isNoContent());

        verify(authService).logout(user);
    }

    @Test
    void me_withAuthenticatedUser_returnsUserInfo() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));

        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@example.com"));
    }

    @Test
    void forgotPassword_success_returnsAccepted() throws Exception {
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ForgotPasswordRequest("test@example.com"))))
                .andExpect(status().isAccepted());

        verify(authService).forgotPassword(any(ForgotPasswordRequest.class));
    }

    @Test
    void resetPassword_success_returnsNoContent() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResetPasswordRequest("token123", "newPass123"))))
                .andExpect(status().isNoContent());

        verify(authService).resetPassword(any(ResetPasswordRequest.class));
    }
}
