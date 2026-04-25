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
        Instant cancellationPolicyAcceptedAt,
        Instant createdAt,
        SlotResponse slot
) {}
