package com.authservice.config;

import com.authservice.token.JwtService;
import com.authservice.user.Role;
import com.authservice.user.User;
import com.authservice.user.UserRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock private JwtService jwtService;
    @Mock private UserRepository userRepository;
    @InjectMocks private JwtAuthFilter jwtAuthFilter;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private User buildUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .password("encoded")
                .role(Role.ROLE_USER)
                .build();
    }

    @Test
    void doFilter_noAuthHeader_continuesChainWithoutAuth() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        jwtAuthFilter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(jwtService, userRepository);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilter_nonBearerAuthHeader_continuesChainWithoutAuth() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        jwtAuthFilter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(jwtService, userRepository);
    }

    @Test
    void doFilter_validToken_setsAuthentication() throws Exception {
        User user = buildUser();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer validToken");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(jwtService.extractEmail("validToken")).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(jwtService.isTokenValid("validToken", user)).thenReturn(true);

        jwtAuthFilter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(user);
        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_invalidToken_continuesChainWithoutSettingAuth() throws Exception {
        User user = buildUser();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalidToken");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(jwtService.extractEmail("invalidToken")).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(jwtService.isTokenValid("invalidToken", user)).thenReturn(false);

        jwtAuthFilter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_tokenThrows_continuesChainWithoutAuth() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer badToken");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(jwtService.extractEmail("badToken")).thenThrow(new RuntimeException("parse error"));

        jwtAuthFilter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_unknownUser_continuesChainWithoutAuth() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer tokenForUnknown");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(jwtService.extractEmail("tokenForUnknown")).thenReturn("unknown@example.com");
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        jwtAuthFilter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }
}
