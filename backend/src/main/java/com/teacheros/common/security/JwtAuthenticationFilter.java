package com.teacheros.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            Optional<AuthenticatedPrincipal> principal = jwtService.parseAndValidate(token);

            if (principal.isPresent()) {
                AuthenticatedPrincipal p = principal.get();
                var authorities = List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                        "ROLE_" + p.role()));
                var authentication = new UsernamePasswordAuthenticationToken(p, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
            // If invalid/expired, we deliberately do NOT set an authentication.
            // Spring Security's authorization rules will then reject the request
            // with 401/403 as appropriate - we don't throw here ourselves.
        }

        filterChain.doFilter(request, response);
    }
}
