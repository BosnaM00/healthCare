package org.example.healthcare.dto.payment;

import org.example.healthcare.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID bookingId,
        String stripePaymentIntentId,
        BigDecimal amount,
        BigDecimal platformFee,
        PaymentStatus status,
        Instant releasedAt,
        Instant refundedAt
) {}
