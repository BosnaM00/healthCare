package org.example.healthcare.dto.medic;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EarningTransactionResponse(
        UUID id,
        String consultationId,
        String patientName,
        Instant date,
        BigDecimal grossAmount,
        BigDecimal platformFee,
        BigDecimal netAmount,
        String currency,
        String status
) {}
