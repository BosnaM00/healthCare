package org.example.healthcare.dto.availability;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

public record AvailabilityResponse(
        UUID id,
        UUID medicId,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        int slotDurationMin,
        int bufferMin
) {}
