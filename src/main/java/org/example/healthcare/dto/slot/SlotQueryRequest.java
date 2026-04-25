package org.example.healthcare.dto.slot;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record SlotQueryRequest(
        @NotNull LocalDate from,
        @NotNull LocalDate to   // max 60-day window enforced in service
) {}
