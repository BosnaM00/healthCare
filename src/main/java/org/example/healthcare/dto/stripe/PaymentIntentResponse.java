package org.example.healthcare.dto.stripe;

import java.util.UUID;

/**
 * Response for {@code POST /api/v1/payments/intents}.
 *
 * @param paymentId      MediConnect payment UUID
 * @param clientSecret   Stripe PaymentIntent client_secret — pass to Stripe.js confirmPayment()
 * @param publishableKey Stripe publishable key — initialise Stripe.js with this value
 */
public record PaymentIntentResponse(UUID paymentId, String clientSecret, String publishableKey) {}
