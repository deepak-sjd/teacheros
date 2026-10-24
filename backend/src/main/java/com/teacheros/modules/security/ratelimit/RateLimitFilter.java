package com.teacheros.modules.security.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teacheros.common.exception.ErrorResponse;
import com.teacheros.common.logging.CorrelationIdFilter;
import com.teacheros.common.security.AuthenticatedPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

/**
 * General API limiter. Created with `new` inside SecurityConfig (NOT a bean) so
 * it runs
 * exactly once, after JwtAuthenticationFilter - it needs the authenticated user
 * to apply the per-user budget. Tight per-IP buckets for /auth/**, public
 * enquiries and the payment webhook; everything else gets a per-IP and a
 * per-user budget. Behind a proxy set server.forward-headers-strategy=native
 * so getRemoteAddr() is the real client.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private record Rule(String name, int limit) {
    }

    private final ApiRateLimiter limiter;
    private final RateLimitProperties props;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(ApiRateLimiter limiter, RateLimitProperties props, ObjectMapper objectMapper) {
        this.limiter = limiter;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/") || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        int window = props.getWindowSeconds();
        Rule rule = ruleFor(path);
        ApiRateLimiter.Decision decision = limiter.tryAcquire("ip:" + rule.name() + ":" + request.getRemoteAddr(),
                rule.limit(), window);

        if (decision.allowed() && rule.name().equals("api")) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof AuthenticatedPrincipal p) {
                decision = limiter.tryAcquire("user:" + p.userId(), props.getUserPerWindow(), window);
            }
        }

        if (!decision.allowed()) {
            reject(response, decision.retryAfterSeconds());
            return;
        }
        chain.doFilter(request, response);
    }

    private Rule ruleFor(String path) {
        if (path.startsWith("/api/v1/auth/")) {
            return new Rule("auth", props.getAuthPerWindow());
        }
        String enquiry = props.getEnquiryPath();
        if (path.equals(enquiry) || path.startsWith(enquiry + "/")) {
            return new Rule("enquiry", props.getEnquiryPerWindow());
        }
        if (path.startsWith(props.getWebhookPathPrefix())) {
            return new Rule("webhook", props.getWebhookPerWindow());
        }
        return new Rule("api", props.getIpPerWindow());
    }

    private void reject(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        long retry = Math.max(1, retryAfterSeconds);
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(retry));
        response.setContentType("application/json");
        objectMapper.writeValue(response.getOutputStream(), new ErrorResponse(
                Instant.now(), 429, "RATE_LIMITED",
                "Too many requests. Try again in " + retry + " second(s).", null,
                MDC.get(CorrelationIdFilter.MDC_KEY)));
    }
}
