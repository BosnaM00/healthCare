package org.example.healthcare.dto.booking;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.example.healthcare.model.ConsultationType;

import java.util.UUID;

public record BookingRequest(
        @NotNull UUID slotId,
        @NotNull UUID medicId,
        @NotNull ConsultationType consultationType,
        @AssertTrue(message = "Must accept cancellation policy") boolean cancellationPolicyAccepted
) {}
