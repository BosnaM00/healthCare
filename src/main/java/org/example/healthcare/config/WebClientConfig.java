package org.example.healthcare.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Registers a prototype {@link WebClient.Builder} bean so that components like
 * {@code DailyVideoProvider} can inject it via constructor injection.
 *
 * <p>In Spring Boot 4.x, {@code WebClient.Builder} is no longer auto-configured
 * in servlet (non-reactive) applications even when {@code spring-boot-starter-webflux}
 * is on the classpath.  This explicit bean restores the same behaviour that was
 * previously provided automatically by {@code WebFluxAutoConfiguration}.
 *
 * <p>The builder is registered with {@code @Bean} scope prototype so each
 * injection site gets its own independent builder instance — consistent with
 * the Spring Boot 3.x contract for this bean.
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
