package com.authservice.token;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenCleanupService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeStaleTokens() {
        int refresh = refreshTokenRepository.deleteStale(LocalDateTime.now());
        int reset = passwordResetTokenRepository.deleteStale(LocalDateTime.now());
        log.info("Token cleanup: removed {} refresh tokens and {} password reset tokens", refresh, reset);
    }
}
