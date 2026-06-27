package com.authservice.token;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenCleanupServiceTest {

    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @InjectMocks private TokenCleanupService tokenCleanupService;

    @Test
    void purgeStaleTokens_deletesFromBothRepositories() {
        when(refreshTokenRepository.deleteStale(any(LocalDateTime.class))).thenReturn(5);
        when(passwordResetTokenRepository.deleteStale(any(LocalDateTime.class))).thenReturn(3);

        tokenCleanupService.purgeStaleTokens();

        verify(refreshTokenRepository).deleteStale(any(LocalDateTime.class));
        verify(passwordResetTokenRepository).deleteStale(any(LocalDateTime.class));
    }
}
