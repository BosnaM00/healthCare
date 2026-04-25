package org.example.healthcare.dto.dispute;

import org.example.healthcare.model.DisputeStatus;

import java.time.Instant;
import java.util.UUID;

public record DisputeResponse(
        UUID id,
        UUID consultationId,
        UUID raisedById,
        String reason,
        DisputeStatus status,
        UUID resolvedById,
        Instant resolvedAt,
        String resolutionNote,
        Instant createdAt
) {}
