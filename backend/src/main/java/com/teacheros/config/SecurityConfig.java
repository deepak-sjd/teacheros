package com.teacheros.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teacheros.common.security.JwtAuthenticationFilter;
import com.teacheros.modules.security.ratelimit.ApiRateLimiter;
import com.teacheros.modules.security.ratelimit.RateLimitFilter;
import com.teacheros.modules.security.ratelimit.RateLimitProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Stateless, JWT-based security configuration.
 *
 * Authorization is enforced HERE and via @PreAuthorize on service/controller
 * methods (see EnableMethodSecurity) - never solely in the Flutter client.
 * Hiding a button in the app is a UX nicety, not a security control.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ApiRateLimiter apiRateLimiter;
    private final RateLimitProperties rateLimitProperties;
    private final ObjectMapper objectMapper;
    private final String allowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
            ApiRateLimiter apiRateLimiter,
            RateLimitProperties rateLimitProperties,
            ObjectMapper objectMapper,
            // Comma-separated exact origins. The dev default covers the Vite
            // dev server; prod must set CORS_ALLOWED_ORIGINS explicitly
            // (ProductionConfigValidator rejects blank or '*' there).
            @Value("${app.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}") String allowedOrigins) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.apiRateLimiter = apiRateLimiter;
        this.rateLimitProperties = rateLimitProperties;
        this.objectMapper = objectMapper;
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // stateless JWT API, not cookie/session based - CSRF does not apply
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/auth/**",
                                "/api/v1/payments/webhook",
                                "/actuator/health",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html")
                        .permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // After the JWT filter so the per-user budget can see the authenticated user.
                // Created with `new` (not a @Bean) so it is registered exactly once.
                .addFilterAfter(new RateLimitFilter(apiRateLimiter, rateLimitProperties, objectMapper),
                        JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        CorsConfiguration configuration = new CorsConfiguration();
        if (origins.contains("*")) {
            // Dev convenience only - the prod validator refuses to boot with this.
            configuration.setAllowedOriginPatterns(List.of("*"));
        } else {
            configuration.setAllowedOrigins(origins);
        }
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
