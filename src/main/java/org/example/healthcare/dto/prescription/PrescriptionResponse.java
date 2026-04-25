package org.example.healthcare.dto.prescription;

import java.time.Instant;
import java.util.UUID;

public record PrescriptionResponse(
        UUID id,
        UUID consultationId,
        /** Decrypted content — returned only to the medic and the patient */
        String content,
        /** Pre-signed S3 URL for PDF download — null until PDF is generated */
        String pdfUrl,
        Instant createdAt
) {}
