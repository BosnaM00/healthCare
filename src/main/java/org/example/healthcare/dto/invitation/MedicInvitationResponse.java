package org.example.healthcare.dto.invitation;

import org.example.healthcare.model.InvitationStatus;

import java.time.Instant;
import java.util.UUID;

public record MedicInvitationResponse(
        UUID id,
        UUID clinicId,
        String email,
        InvitationStatus status,
        Instant createdAt,
        Instant acceptedAt
) {}
