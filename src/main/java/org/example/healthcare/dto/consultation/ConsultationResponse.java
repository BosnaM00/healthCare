package org.example.healthcare.dto.consultation;

import org.example.healthcare.model.ConsultationFailureReason;
import org.example.healthcare.model.ConsultationStatus;

import java.time.Instant;
import java.util.UUID;

public record ConsultationResponse(
        UUID id,
        UUID bookingId,
        ConsultationStatus status,
        /** Provider-internal room name. */
        String videoRoomId,
        /** Full join URL — available once room is created at booking-confirmed time. */
        String videoRoomUrl,
        /** Video provider name, e.g. "daily". */
        String videoProvider,
        Instant startedAt,
        Instant endedAt,
        Integer durationSeconds,
        /** Decrypted notes — only returned to the medic and authorised parties. */
        String notes,
        Instant releaseAt,
        /** Populated when status == FAILED; null otherwise. */
        ConsultationFailureReason failureReason
) {}
