package org.example.healthcare.dto.consultation;

import org.example.healthcare.model.ConsultationStatus;

import java.time.Instant;
import java.util.UUID;

public record ConsultationResponse(
        UUID id,
        UUID bookingId,
        ConsultationStatus status,
        String videoRoomId,
        Instant startedAt,
        Instant endedAt,
        Integer durationSeconds,
        /** Decrypted notes — only returned to the medic and authorised parties */
        String notes,
        Instant releaseAt
) {}
