package org.example.healthcare.dto.consultation;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for GET /consultations/{id}/notes.
 * Maps the encrypted notes stored on the Consultation entity to the shape
 * expected by the UI's ConsultationNote type.
 */
public record ConsultationNoteResponse(
        UUID id,
        UUID consultationId,
        String content,
        String template,
        Instant updatedAt,
        boolean isDraft
) {}
