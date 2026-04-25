package org.example.healthcare.dto.clinic;

import org.example.healthcare.model.ClinicStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ClinicResponse(
        UUID id,
        String name,
        String cui,
        ClinicStatus status,
        String stripeAccountId,
        BigDecimal commissionRate,
        Instant createdAt
) {}
