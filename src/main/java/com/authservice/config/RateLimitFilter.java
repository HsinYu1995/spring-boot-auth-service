package com.authservice.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> RATE_LIMITED_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/forgot-password"
    );

    // Requests allowed per minute per IP per endpoint
    private static final Map<String, Integer> PATH_CAPACITY = Map.of(
            "/api/v1/auth/login",           10,
            "/api/v1/auth/register",        20,
            "/api/v1/auth/forgot-password",  5
    );

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        if (!RATE_LIMITED_PATHS.contains(path)) {
            chain.doFilter(request, response);
            return;
        }

        String ip = resolveClientIp(request);
        String key = ip + ":" + path;
        int capacity = PATH_CAPACITY.get(path);
        Bucket bucket = buckets.computeIfAbsent(key, k -> buildBucket(capacity));

        var probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            chain.doFilter(request, response);
        } else {
            long retryAfterSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000L;
            log.warn("Rate limit exceeded for IP {} on {}", ip, path);
            response.setStatus(429);
            response.setContentType("application/json");
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.getWriter().write("""
                    {"status":429,"error":"Too Many Requests","message":"Rate limit exceeded. Retry after %d seconds."}
                    """.formatted(retryAfterSeconds));
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static Bucket buildBucket(int capacity) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillIntervally(capacity, Duration.ofMinutes(1))
                        .build())
                .build();
    }
}
