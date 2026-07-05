package com.example.HealthAssistBackend.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate limiting filter to protect APIs from abuse and cost spikes.
 * Uses in-memory token bucket per IP address.
 */
@Component
public class RateLimitFilter implements Filter {

    @Value("${healthassist.rate-limit.max-requests-per-minute:30}")
    private int maxRequestsPerMinute;

    private final Map<String, RateBucket> buckets = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String path = httpRequest.getRequestURI();

        // Only rate-limit AI endpoints
        if (path.startsWith("/ai/")) {
            String clientIp = httpRequest.getRemoteAddr();
            RateBucket bucket = buckets.computeIfAbsent(clientIp, k -> new RateBucket());

            if (!bucket.tryConsume()) {
                HttpServletResponse httpResponse = (HttpServletResponse) response;
                httpResponse.setStatus(429);
                httpResponse.setContentType("application/json");
                httpResponse.getWriter().write(
                    "{\"error\": \"Rate limit exceeded. Maximum " + maxRequestsPerMinute + " requests per minute.\"}");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    /**
     * Simple token bucket rate limiter per client.
     */
    private class RateBucket {
        private final AtomicInteger tokens;
        private volatile long lastRefill;

        RateBucket() {
            this.tokens = new AtomicInteger(maxRequestsPerMinute);
            this.lastRefill = System.currentTimeMillis();
        }

        boolean tryConsume() {
            refillIfNeeded();
            return tokens.getAndDecrement() > 0;
        }

        private void refillIfNeeded() {
            long now = System.currentTimeMillis();
            if (now - lastRefill >= 60_000) {
                tokens.set(maxRequestsPerMinute);
                lastRefill = now;
            }
        }
    }
}
