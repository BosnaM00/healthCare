package org.example.healthcare.dto.medic;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record MedicRequest(
        @NotBlank String licenseNumber,
        @NotNull LocalDate licenseExpiresAt,
        UUID clinicId,                          // nullable = independent practitioner
        boolean availableForInstant,
        @NotEmpty List<UUID> specialtyIds
) {}
