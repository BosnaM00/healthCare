package org.example.healthcare.dto.medic;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record MedicSearchRequest(
        UUID specialtyId,
        UUID clinicId,
        Boolean instantOnly,
        @PositiveOrZero int page,
        @Positive int size
) {}
