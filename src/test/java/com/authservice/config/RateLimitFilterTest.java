package com.authservice.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RateLimitFilterTest {

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter();
    }

    @Test
    void doFilter_nonRateLimitedPath_passesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        request.setRequestURI("/api/v1/auth/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isNotEqualTo(429);
    }

    @Test
    void doFilter_rateLimitedPath_underLimit_passesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRequestURI("/api/v1/auth/login");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isNotEqualTo(429);
    }

    @Test
    void doFilter_rateLimitedPath_overLimit_returns429() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        String ip = "192.168.99.1";

        // login capacity = 10; exhaust the bucket
        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            req.setRequestURI("/api/v1/auth/login");
            req.setRemoteAddr(ip);
            filter.doFilter(req, new MockHttpServletResponse(), chain);
        }

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRequestURI("/api/v1/auth/login");
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    void doFilter_withXForwardedForHeader_usesForwardedIp() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRequestURI("/api/v1/auth/login");
        request.addHeader("X-Forwarded-For", "203.0.113.1, 10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        // Should pass through (first request under limit), verifying X-Forwarded-For was used
        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_forgotPassword_hasLowerLimit() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        String ip = "192.168.99.2";

        // forgot-password capacity = 5; exhaust it
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/forgot-password");
            req.setRequestURI("/api/v1/auth/forgot-password");
            req.setRemoteAddr(ip);
            filter.doFilter(req, new MockHttpServletResponse(), chain);
        }

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/forgot-password");
        request.setRequestURI("/api/v1/auth/forgot-password");
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
    }
}
