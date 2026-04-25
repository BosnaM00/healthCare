package org.example.healthcare.dto.availability;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record AvailabilityRequest(
        @NotNull DayOfWeek dayOfWeek,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @Positive int slotDurationMin,
        @PositiveOrZero int bufferMin
) {}
