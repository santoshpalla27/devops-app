package com.platform.controlplane.security;

import com.platform.controlplane.error.ErrorCode;
import com.platform.controlplane.error.ErrorResponse;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting filter for sensitive endpoints.
 * 
 * Uses token bucket algorithm per client IP.
 * Strict limits on:
 * - Chaos injection endpoints
 * - Policy mutation endpoints
 * - WebSocket connections
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitingFilter implements Filter {
    
    // Rate limit buckets per IP
    private final Map<String, Bucket> chaosRateLimiters = new ConcurrentHashMap<>();
    private final Map<String, Bucket> policyRateLimiters = new ConcurrentHashMap<>();
    private final Map<String, Bucket> globalRateLimiters = new ConcurrentHashMap<>();
    
    @Value("${controlplane.security.rate-limit.chaos.requests-per-minute:10}")
    private int chaosRequestsPerMinute;
    
    @Value("${controlplane.security.rate-limit.policy.requests-per-minute:30}")
    private int policyRequestsPerMinute;
    
    @Value("${controlplane.security.rate-limit.global.requests-per-minute:100}")
    private int globalRequestsPerMinute;
    
    @Value("${controlplane.security.rate-limit.enabled:true}")
    private boolean enabled;
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        if (!enabled) {
            chain.doFilter(request, response);
            return;
        }
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        String clientIp = getClientIp(httpRequest);
        String path = httpRequest.getRequestURI();
        String method = httpRequest.getMethod();
        
        // Check rate limits based on endpoint
        RateLimitResult result = checkRateLimit(clientIp, path, method);
        
        if (!result.allowed) {
            log.warn("[RATE_LIMIT] Blocked {} {} from {} - bucket: {}", 
                method, path, clientIp, result.bucketType);
            
            httpResponse.setStatus(429);
            httpResponse.setContentType("application/json");
            httpResponse.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds));
            httpResponse.setHeader("X-RateLimit-Limit", String.valueOf(result.limit));
            httpResponse.setHeader("X-RateLimit-Remaining", "0");
            
            ErrorResponse error = ErrorResponse.builder()
                .code("CP-429")
                .message("Rate limit exceeded")
                .detail("Too many requests to " + result.bucketType + " endpoints")
                .fatal(false)
                .status(429)
                .timestamp(Instant.now())
                .path(path)
                .metadata(Map.of(
                    "retryAfterSeconds", result.retryAfterSeconds,
                    "bucketType", result.bucketType
                ))
                .build();
            
            httpResponse.getWriter().write(toJson(error));
            return;
        }
        
        chain.doFilter(request, response);
    }
    
    private RateLimitResult checkRateLimit(String clientIp, String path, String method) {
        // Chaos endpoints - very strict
        if (path.startsWith("/api/chaos") && isMutatingMethod(method)) {
            Bucket bucket = chaosRateLimiters.computeIfAbsent(clientIp, 
                ip -> createBucket(chaosRequestsPerMinute));
            
            if (!bucket.tryConsume(1)) {
                return new RateLimitResult(false, "chaos", chaosRequestsPerMinute, 60);
            }
        }
        
        // Policy endpoints - strict for mutations
        if (path.startsWith("/api/policies") && isMutatingMethod(method)) {
            Bucket bucket = policyRateLimiters.computeIfAbsent(clientIp, 
                ip -> createBucket(policyRequestsPerMinute));
            
            if (!bucket.tryConsume(1)) {
                return new RateLimitResult(false, "policy", policyRequestsPerMinute, 60);
            }
        }
        
        // Global rate limit
        Bucket bucket = globalRateLimiters.computeIfAbsent(clientIp, 
            ip -> createBucket(globalRequestsPerMinute));
        
        if (!bucket.tryConsume(1)) {
            return new RateLimitResult(false, "global", globalRequestsPerMinute, 60);
        }
        
        return new RateLimitResult(true, null, 0, 0);
    }
    
    private Bucket createBucket(int requestsPerMinute) {
        Bandwidth limit = Bandwidth.classic(
            requestsPerMinute, 
            Refill.greedy(requestsPerMinute, Duration.ofMinutes(1))
        );
        return Bucket.builder().addLimit(limit).build();
    }
    
    private boolean isMutatingMethod(String method) {
        return "POST".equals(method) || "PUT".equals(method) || 
               "PATCH".equals(method) || "DELETE".equals(method);
    }
    
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
    
    private String toJson(ErrorResponse error) {
        return String.format(
            "{\"code\":\"%s\",\"message\":\"%s\",\"detail\":\"%s\",\"status\":%d,\"timestamp\":\"%s\"}",
            error.getCode(), error.getMessage(), error.getDetail(), 
            error.getStatus(), error.getTimestamp()
        );
    }
    
    private record RateLimitResult(boolean allowed, String bucketType, int limit, int retryAfterSeconds) {}
}
