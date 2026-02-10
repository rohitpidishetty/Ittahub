package com.ittahub.ITTaHub.Filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimiterFilter extends OncePerRequestFilter {

    private static class TokenBucket {
        final int capacity;
        final long refillIntervalMs;
        volatile long lastRefillTime;
        AtomicInteger tokens;

        TokenBucket(int capacity, long refillIntervalMs) {
            this.capacity = capacity;
            this.refillIntervalMs = refillIntervalMs;
            this.tokens = new AtomicInteger(capacity);
            this.lastRefillTime = System.currentTimeMillis();
        }

        synchronized boolean allowRequest() {
            long now = System.currentTimeMillis();
            long intervals = (now - lastRefillTime) / refillIntervalMs;
            if (intervals > 0) {
                int refill = (int) intervals;
                tokens.set(Math.min(capacity, tokens.get() + refill));
                lastRefillTime += (refill * refillIntervalMs);
            }

            if (tokens.get() > 0) {
                tokens.decrementAndGet();
                return true;
            }
            return false;
        }
    }

    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final int CAPACITY = 5;
    private final long REFILL_INTERVAL = 12000; // 1 token per 12s → 5 requests/min

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        return path.startsWith("/auth-service/is-authorized") || path.startsWith("/auth-service/user-repositories") || path.startsWith("/auth-service/repository-content") || path.startsWith("/auth-service/shared-repository-content");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setHeader("Access-Control-Allow-Origin", "https://ittahub.web.app");
            response.setHeader("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
            response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
            response.setHeader("Access-Control-Allow-Credentials", "true");
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        String clientIP = request.getHeader("X-Forwarded-For"); // Real IP address
//        System.out.println(clientIP);

        if (clientIP != null && !clientIP.isBlank()) {
            clientIP = clientIP.split(",")[0].trim();
        } else {
            clientIP = request.getRemoteAddr(); // Fallback
        }
        TokenBucket token;

        if (!buckets.containsKey(clientIP)) {
            token = new TokenBucket(CAPACITY, REFILL_INTERVAL);
            buckets.put(clientIP, token);
        } else
            token = buckets.get(clientIP);

        if (!token.allowRequest()) {

            response.setHeader("Access-Control-Allow-Origin", "https://ittahub.web.app");
            response.setHeader("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
            response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
            response.setHeader("Access-Control-Allow-Credentials", "true");

            response.setStatus(429);
            response.setContentType("text/plain");
            response.getWriter().write("Too many requests. Please try again later.");
            response.getWriter().flush();
            return;
        }

        filterChain.doFilter(request, response);
    }

}

