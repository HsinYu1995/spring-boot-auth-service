package com.authservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @InjectMocks private GlobalExceptionHandler handler;
    @Mock private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        when(request.getRequestURI()).thenReturn("/api/v1/auth/test");
    }

    @Test
    void handleUserAlreadyExists_returns409() {
        ResponseEntity<ErrorResponse> response = handler.handleUserAlreadyExists(
                new UserAlreadyExistsException("Email already registered"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Email already registered");
        assertThat(response.getBody().status()).isEqualTo(409);
    }

    @Test
    void handleUserNotFound_returns404() {
        ResponseEntity<ErrorResponse> response = handler.handleUserNotFound(
                new UserNotFoundException("User not found"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(404);
    }

    @Test
    void handleAuthException_returns401() {
        ResponseEntity<ErrorResponse> response = handler.handleAuthException(
                new AuthException("Invalid credentials"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Invalid credentials");
    }

    @Test
    void handleTokenException_returns401() {
        ResponseEntity<ErrorResponse> response = handler.handleTokenException(
                new TokenException("Invalid token"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(401);
    }

    @Test
    void handleValidationErrors_returns400WithFieldMessages() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(
                new FieldError("obj", "email", "must be a valid email")));

        ResponseEntity<ErrorResponse> response = handler.handleValidationErrors(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("must be a valid email");
    }

    @Test
    void handleGeneral_returns500() {
        ResponseEntity<ErrorResponse> response = handler.handleGeneral(
                new RuntimeException("Something went wrong"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(500);
    }
}
