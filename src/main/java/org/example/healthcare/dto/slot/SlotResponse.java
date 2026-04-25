package org.example.healthcare.dto.slot;

import org.example.healthcare.model.SlotStatus;

import java.time.Instant;
import java.util.UUID;

public record SlotResponse(
        UUID id,
        UUID medicId,
        Instant startsAt,
        Instant endsAt,
        SlotStatus status
) {}
