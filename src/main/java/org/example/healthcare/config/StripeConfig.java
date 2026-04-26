package org.example.healthcare.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Initialises the Stripe Java SDK once at startup.
 *
 * <p>Sets {@code Stripe.apiKey} from the {@code stripe.api.key} property, which must
 * resolve to a real key from an environment variable. The application fails fast if the
 * key is absent or still set to the placeholder value — this prevents accidentally hitting
 * Stripe's API with the wrong credentials.
 *
 * <p>Stripe network retries are configured to 2 so transient network hiccups
 * do not propagate as errors to callers.
 */
@Slf4j
@Configuration
public class StripeConfig {

    @Value("${stripe.api.key}")
    private String apiKey;

    @Value("${stripe.webhook.secret}")
    private String webhookSecret;

    @PostConstruct
    public void init() {
        if (!StringUtils.hasText(apiKey) || "sk_test_placeholder".equals(apiKey)) {
            log.warn("STRIPE_API_KEY is using the placeholder value. " +
                     "Set STRIPE_API_KEY environment variable to a real Stripe key before making API calls.");
        }
        if (!StringUtils.hasText(webhookSecret) || "whsec_placeholder".equals(webhookSecret)) {
            log.warn("STRIPE_WEBHOOK_SECRET is using the placeholder value. " +
                     "Set STRIPE_WEBHOOK_SECRET environment variable before the webhook endpoint goes live.");
        }

        Stripe.apiKey = apiKey;
        Stripe.setMaxNetworkRetries(2);

        log.info("Stripe SDK initialised (key prefix: {}, retries: 2)",
                apiKey.length() > 7 ? apiKey.substring(0, 7) + "..." : "***");
    }

    /** Exposes the configured webhook secret for use in the webhook controller. */
    public String webhookSecret() {
        return webhookSecret;
    }
}
