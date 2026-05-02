package org.example.healthcare.video;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Strongly-typed configuration for the video subsystem.
 *
 * <p>Bound from the {@code app.video.*} namespace in {@code application.properties}.
 * Real secrets ({@code apiKey}, {@code webhookSecret}) must arrive via environment
 * variables or AWS Secrets Manager — never committed in plain text.
 *
 * <p>The {@code enabled} flag acts as a master feature gate. When {@code false},
 * {@link DailyVideoProvider} skips all network calls and returns deterministic
 * stub responses, allowing the rest of the booking flow to work in CI.
 */
@Component
@ConfigurationProperties(prefix = "app.video")
@Getter
@Setter
public class VideoConfig {

    /** Master feature gate — false disables all Daily API calls. */
    private boolean enabled = false;

    /** Active provider identifier (currently only "daily" is supported). */
    private String provider = "daily";

    /** Default token time-to-live for /join tokens. */
    private Duration tokenTtl = Duration.ofMinutes(15);

    /** Maximum lifetime of a video room from scheduled start. */
    private Duration maxRoomTtl = Duration.ofHours(2);

    /** Nested Daily.co-specific settings. */
    private Daily daily = new Daily();

    @Getter
    @Setter
    public static class Daily {

        /** Base URL for the Daily REST API. */
        private String apiBase = "https://api.daily.co/v1";

        /** Daily API key — injected from ${DAILY_API_KEY}. */
        private String apiKey = "";

        /** Daily subdomain used to construct room URLs. */
        private String domain = "mediconnect.daily.co";

        /** Region affinity header for GDPR EU data-residency (eu-west = Frankfurt/Dublin). */
        private String region = "eu-west";

        /** Webhook secret for HMAC-SHA256 signature verification — from ${DAILY_WEBHOOK_SECRET}. */
        private String webhookSecret = "";
    }
}
