package com.authservice.auth;

import com.authservice.auth.dto.*;
import com.authservice.exception.AuthException;
import com.authservice.exception.TokenException;
import com.authservice.exception.UserAlreadyExistsException;
import com.authservice.token.*;
import com.authservice.user.Role;
import com.authservice.user.User;
import com.authservice.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private EmailService emailService;
    @Mock private JwtProperties jwtProperties;
    @InjectMocks private AuthService authService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .password("encodedPassword")
                .role(Role.ROLE_USER)
                .build();
    }

    @Test
    void register_success_returnsTokenPair() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(jwtService.generateAccessToken(any(User.class))).thenReturn("accessToken");
        when(refreshTokenService.createRefreshToken(any(User.class))).thenReturn("refreshToken");
        when(jwtProperties.getAccessTokenExpiration()).thenReturn(3600000L);

        AuthTokenPair result = authService.register(new RegisterRequest("test@example.com", "password123"));

        assertThat(result.accessToken()).isEqualTo("accessToken");
        assertThat(result.refreshToken()).isEqualTo("refreshToken");
    }

    @Test
    void register_duplicateEmail_throwsUserAlreadyExistsException() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("test@example.com", "password123")))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    void login_validCredentials_returnsTokenPair() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(user)).thenReturn("accessToken");
        when(refreshTokenService.createRefreshToken(user)).thenReturn("refreshToken");
        when(jwtProperties.getAccessTokenExpiration()).thenReturn(3600000L);

        AuthTokenPair result = authService.login(new LoginRequest("test@example.com", "password123"));

        assertThat(result.accessToken()).isEqualTo("accessToken");
    }

    @Test
    void login_badCredentials_throwsAuthException() {
        doThrow(new BadCredentialsException("bad"))
                .when(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));

        assertThatThrownBy(() -> authService.login(new LoginRequest("test@example.com", "wrong")))
                .isInstanceOf(AuthException.class);
    }

    @Test
    void refresh_validToken_returnsNewTokenPair() {
        doAnswer(inv -> {
            String[] holder = inv.getArgument(1);
            holder[0] = "newRefreshToken";
            return user;
        }).when(refreshTokenService).validateAndRotate(eq("rawToken"), any(String[].class));
        when(jwtService.generateAccessToken(user)).thenReturn("newAccessToken");
        when(jwtProperties.getAccessTokenExpiration()).thenReturn(3600000L);

        AuthTokenPair result = authService.refresh("rawToken");

        assertThat(result.accessToken()).isEqualTo("newAccessToken");
        assertThat(result.refreshToken()).isEqualTo("newRefreshToken");
    }

    @Test
    void logout_revokesAllUserTokens() {
        authService.logout(user);
        verify(refreshTokenService).revokeAllForUser(user);
    }

    @Test
    void forgotPassword_userExists_savesResetTokenAndSendsEmail() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        authService.forgotPassword(new ForgotPasswordRequest("test@example.com"));

        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
        verify(emailService).sendPasswordResetEmail(eq("test@example.com"), anyString());
    }

    @Test
    void forgotPassword_userNotFound_doesNothing() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        authService.forgotPassword(new ForgotPasswordRequest("unknown@example.com"));

        verifyNoInteractions(passwordResetTokenRepository, emailService);
    }

    @Test
    void resetPassword_validToken_updatesPasswordAndRevokesTokens() {
        PasswordResetToken resetToken = mock(PasswordResetToken.class);
        when(passwordResetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(resetToken));
        when(resetToken.isUsed()).thenReturn(false);
        when(resetToken.isExpired()).thenReturn(false);
        when(resetToken.getUser()).thenReturn(user);
        when(passwordEncoder.encode("newPassword")).thenReturn("encodedNew");

        authService.resetPassword(new ResetPasswordRequest("someToken", "newPassword"));

        verify(userRepository).save(user);
        verify(resetToken).setUsed(true);
        verify(passwordResetTokenRepository).save(resetToken);
        verify(refreshTokenService).revokeAllForUser(user);
    }

    @Test
    void resetPassword_tokenNotFound_throwsTokenException() {
        when(passwordResetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequest("invalid", "newPass123")))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("Invalid or expired");
    }

    @Test
    void resetPassword_usedToken_throwsTokenException() {
        PasswordResetToken resetToken = mock(PasswordResetToken.class);
        when(passwordResetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(resetToken));
        when(resetToken.isUsed()).thenReturn(true);

        assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequest("usedToken", "newPass123")))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("already used");
    }

    @Test
    void resetPassword_expiredToken_throwsTokenException() {
        PasswordResetToken resetToken = mock(PasswordResetToken.class);
        when(passwordResetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(resetToken));
        when(resetToken.isUsed()).thenReturn(false);
        when(resetToken.isExpired()).thenReturn(true);

        assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequest("expiredToken", "newPass123")))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("expired");
    }
}
