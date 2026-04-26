package org.example.healthcare.dto.stripe;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/payments/intents}.
 */
public record PaymentIntentRequest(@NotNull UUID bookingId) {}
