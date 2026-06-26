package com.example.clinic.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
@Component
public class LoginRateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimitInterceptor.class);
    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_MS   = 60_000L;
    private final Map<String, Deque<Long>> windowByKey = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
        if (!"POST".equalsIgnoreCase(req.getMethod())) return true;

        String ip   = resolveClientIp(req);
        String path = req.getRequestURI();
        String key  = ip + "::" + path;

        long now   = System.currentTimeMillis();
        long cutoff = now - WINDOW_MS;

        Deque<Long> window = windowByKey.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && window.peekFirst() < cutoff) window.pollFirst();
            if (window.size() >= MAX_ATTEMPTS) {
                long retryAfter = (window.peekFirst() + WINDOW_MS - now) / 1000;
                log.warn("Rate limit depasit: ip={} path={} - {} incercari in ultimele 60s", ip, path, window.size());
                res.setStatus(429);
                res.setHeader("Retry-After", String.valueOf(retryAfter));
                String redirect = path.contains("forgot-password")
                        ? "/forgot-password?error=rateLimited"
                        : "/login?error=rateLimited";
                res.sendRedirect(redirect);
                return false;
            }
            window.addLast(now);
        }
        return true;
    }

    private String resolveClientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
