package com.teacheros.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Attaches a correlation ID to every request so a single request can be
 * traced end-to-end through logs, even across async/background work.
 *
 * The ID is accepted from an incoming X-Correlation-Id header if present
 * (useful when the mobile client generates one), otherwise a new one is
 * generated. It is echoed back in the response and exposed to the
 * global exception handler so error responses include a traceId the
 * user can quote when reporting an issue.
 */
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String correlationId = request.getHeader(HEADER_NAME);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER_NAME, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Always clear MDC - this thread will be reused by the pool for
            // another request and must not leak correlation IDs across requests.
            MDC.remove(MDC_KEY);
        }
    }
}
