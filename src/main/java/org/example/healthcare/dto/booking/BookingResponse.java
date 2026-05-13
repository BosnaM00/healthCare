package org.example.healthcare.dto.booking;

import org.example.healthcare.dto.slot.SlotResponse;
import org.example.healthcare.model.BookingPaymentStatus;
import org.example.healthcare.model.ConsultationType;

import java.time.Instant;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        UUID patientId,
        UUID medicId,
        UUID slotId,
        ConsultationType consultationType,
        BookingPaymentStatus paymentStatus,
        /** Derived booking lifecycle status for the UI (SCHEDULED, CONFIRMED, COMPLETED, CANCELLED). */
        String bookingStatus,
        Instant cancellationPolicyAcceptedAt,
        Instant createdAt,
        SlotResponse slot,
        /** Medic display info — null only if the medic record is unavailable. */
        MedicInfo medic
) {
    public record MedicInfo(UUID id, UUID userId, String firstName, String lastName) {}
}
