package org.example.healthcare.dto.medic;

import java.math.BigDecimal;

public record EarningsSummaryResponse(
        BigDecimal totalGross,
        BigDecimal platformFee,
        BigDecimal totalNet,
        String currency,
        String period,
        long consultationCount,
        BigDecimal avgPerConsultation
) {}
