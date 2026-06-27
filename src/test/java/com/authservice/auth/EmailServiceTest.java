package com.authservice.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock private JavaMailSender mailSender;
    @InjectMocks private EmailService emailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "fromAddress", "noreply@example.com");
        ReflectionTestUtils.setField(emailService, "baseUrl", "http://localhost:8080");
    }

    @Test
    void sendPasswordResetEmail_sendsEmailWithCorrectDetails() {
        // @Async is not active outside Spring context — method runs synchronously
        emailService.sendPasswordResetEmail("user@example.com", "reset-token-123");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage msg = captor.getValue();
        assertThat(msg.getFrom()).isEqualTo("noreply@example.com");
        assertThat(msg.getTo()).containsExactly("user@example.com");
        assertThat(msg.getText()).contains("reset-token-123");
        assertThat(msg.getSubject()).isEqualTo("Password Reset Request");
    }

    @Test
    void sendPasswordResetEmail_whenMailSenderThrows_doesNotPropagateException() {
        doThrow(new RuntimeException("SMTP error")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> emailService.sendPasswordResetEmail("user@example.com", "token"))
                .doesNotThrowAnyException();
    }
}
