package org.example.healthcare.dto.medic;

import java.time.Instant;

/**
 * Patient document metadata. There is no documents table in the current schema, so
 * this endpoint returns an empty list; the record matches the frontend
 * {@code PatientDocument} shape.
 */
public record PatientDocumentResponse(
        String id,
        String patientId,
        Instant uploadedAt,
        String name,
        String type,
        String mimeType,
        long sizeBytes,
        String url,
        String uploadedByMedicId
) {}
