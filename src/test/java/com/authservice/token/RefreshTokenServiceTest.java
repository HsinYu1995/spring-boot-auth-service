package com.authservice.token;

import com.authservice.exception.TokenException;
import com.authservice.user.Role;
import com.authservice.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private JwtProperties jwtProperties;
    @InjectMocks private RefreshTokenService refreshTokenService;

    private User buildUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .password("encoded")
                .role(Role.ROLE_USER)
                .build();
    }

    @Test
    void createRefreshToken_savesTokenAndReturnsRaw() {
        User user = buildUser();
        when(jwtProperties.getRefreshTokenExpiration()).thenReturn(604800000L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        String rawToken = refreshTokenService.createRefreshToken(user);

        assertThat(rawToken).isNotNull().isNotBlank();
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isEqualTo(user);
        assertThat(captor.getValue().getTokenHash()).isNotBlank();
    }

    @Test
    void validateAndRotate_validToken_returnsUserAndRotates() {
        User user = buildUser();
        RefreshToken storedToken = mock(RefreshToken.class);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(storedToken));
        when(storedToken.isRevoked()).thenReturn(false);
        when(storedToken.isExpired()).thenReturn(false);
        when(storedToken.getUser()).thenReturn(user);
        when(jwtProperties.getRefreshTokenExpiration()).thenReturn(604800000L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        String[] holder = new String[1];
        User result = refreshTokenService.validateAndRotate("rawToken", holder);

        assertThat(result).isEqualTo(user);
        assertThat(holder[0]).isNotBlank();
        verify(storedToken).setRevoked(true);
    }

    @Test
    void validateAndRotate_tokenNotFound_throwsTokenException() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.validateAndRotate("invalid", new String[1]))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("Invalid refresh token");
    }

    @Test
    void validateAndRotate_revokedToken_revokesAllAndThrows() {
        User user = buildUser();
        RefreshToken storedToken = mock(RefreshToken.class);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(storedToken));
        when(storedToken.isRevoked()).thenReturn(true);
        when(storedToken.getUser()).thenReturn(user);

        assertThatThrownBy(() -> refreshTokenService.validateAndRotate("reusedToken", new String[1]))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("reuse detected");

        verify(refreshTokenRepository).revokeAllByUser(user);
    }

    @Test
    void validateAndRotate_expiredToken_throwsTokenException() {
        RefreshToken storedToken = mock(RefreshToken.class);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(storedToken));
        when(storedToken.isRevoked()).thenReturn(false);
        when(storedToken.isExpired()).thenReturn(true);

        assertThatThrownBy(() -> refreshTokenService.validateAndRotate("expiredToken", new String[1]))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void revokeAllForUser_delegatesToRepository() {
        User user = buildUser();
        refreshTokenService.revokeAllForUser(user);
        verify(refreshTokenRepository).revokeAllByUser(user);
    }
}
