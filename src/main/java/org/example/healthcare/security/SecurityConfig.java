package org.example.healthcare.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Stateless Spring Security configuration for the MediConnect API.
 *
 * <p>Key decisions:
 * <ul>
 *   <li>JWT-based authentication via {@link JwtAuthFilter}.</li>
 *   <li>The Stripe webhook endpoint ({@code POST /api/v1/webhooks/stripe}) is public
 *       because Stripe calls it server-to-server, but it is also excluded from CORS
 *       since Stripe does not make browser requests.</li>
 *   <li>CORS is configured only for browser-originated origins; the webhook path
 *       is explicitly omitted from the CORS allow-list.</li>
 *   <li>{@code @EnableMethodSecurity} enables {@code @PreAuthorize} on service methods.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:3000}")
    private String allowedOriginsRaw;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // ── Webhook endpoints — public, signature-verified inside each handler ──
                .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/stripe").permitAll()
                // Daily.co video webhook — HMAC-SHA256 verified in VideoWebhookController
                .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/video").permitAll()
                // ── User registration & auth ───────────────────────────────────────
                .requestMatchers(HttpMethod.POST, "/api/v1/users").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/google").permitAll()
                // ── Public read-only endpoints ─────────────────────────────────────
                .requestMatchers(HttpMethod.GET,
                        "/api/v1/medics", "/api/v1/medics/{id}").permitAll()
                .requestMatchers(HttpMethod.GET,
                        "/api/v1/medics/{id}/availabilities").permitAll()
                .requestMatchers(HttpMethod.GET,
                        "/api/v1/medics/{id}/slots").permitAll()
                .requestMatchers(HttpMethod.GET,
                        "/api/v1/specialties", "/api/v1/specialties/{id}").permitAll()
                // ── Everything else requires a valid JWT ───────────────────────────
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS policy: allow only the configured frontend origins.
     * The Stripe webhook path is intentionally omitted — Stripe calls server-to-server
     * and must NOT receive CORS headers.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        List<String> origins = Arrays.stream(allowedOriginsRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Apply CORS to all API paths EXCEPT the Stripe webhook endpoint
        source.registerCorsConfiguration("/api/v1/**", config);

        // No CORS registration for /api/v1/webhooks/stripe — Stripe is server-to-server
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
