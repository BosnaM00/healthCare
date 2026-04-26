package org.example.healthcare.dto.stripe;

/**
 * Response for the medic onboarding-link endpoints.
 *
 * @param url         Stripe Account Link URL — redirect the browser here to begin / resume onboarding
 * @param expiresAt   Unix timestamp (seconds) when the link expires (from Stripe)
 */
public record StripeOnboardingResponse(String url, long expiresAt) {}
