package org.example.healthcare.dto.stripe;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/v1/payments/{id}/refund}.
 */
public record RefundRequest(@NotBlank String reason) {}
