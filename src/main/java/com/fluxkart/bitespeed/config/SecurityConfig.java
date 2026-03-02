package com.fluxkart.bitespeed.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for the Bitespeed Identity Reconciliation Service.
 * <p>
 * Permits unauthenticated access to the public API endpoints
 * ({@code /identify} and {@code /health}) while keeping Spring Security
 * on the classpath for future use (e.g. admin endpoints, rate limiting).
 * </p>
 *
 * @author fl4nk3r
 * @version 1.0
 * @since 2026-03-01
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Configures the HTTP security filter chain.
     * <ul>
     * <li>CSRF disabled — this is a stateless REST API.</li>
     * <li>Session management set to stateless.</li>
     * <li>{@code /identify} and {@code /health} are publicly accessible.</li>
     * <li>All other endpoints require authentication.</li>
     * </ul>
     *
     * @param http the {@link HttpSecurity} builder
     * @return the configured {@link SecurityFilterChain}
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/identify", "/health").permitAll()
                        .anyRequest().authenticated());

        return http.build();
    }
}
