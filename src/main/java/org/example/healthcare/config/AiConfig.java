package org.example.healthcare.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Strongly-typed configuration for the AI decision-support subsystem.
 *
 * <p>Bound from the {@code app.ai.*} namespace in {@code application.properties}.
 * The real secret ({@code apiKey}) must arrive via an environment variable or
 * AWS Secrets Manager — never committed in plain text.
 *
 * <p>The {@code enabled} flag is a master feature gate. When {@code false}
 * (the default), {@code AiDiagnosisServiceImpl} skips the network call and
 * returns a deterministic mock suggestion, so the feature works end-to-end in
 * CI and local dev before an Anthropic key is provisioned.
 */
@Component
@ConfigurationProperties(prefix = "app.ai")
@Getter
@Setter
public class AiConfig {

    /** Master feature gate — false returns mock suggestions instead of calling the model. */
    private boolean enabled = false;

    /** Base URL for the Anthropic Messages API. */
    private String apiBase = "https://api.anthropic.com/v1";

    /** Anthropic API key — injected from ${ANTHROPIC_API_KEY}. */
    private String apiKey = "";

    /** Anthropic API version header value. */
    private String apiVersion = "2023-06-01";

    /** Model id used for diagnosis inference (Haiku keeps this cheap). */
    private String model = "claude-haiku-4-5-20251001";

    /** Upper bound on response tokens. */
    private int maxTokens = 512;
}
