package org.example.healthcare.dto.medic;

import java.time.Instant;
import java.util.List;

/**
 * Prescription as shown on the medic's patient-record page.
 *
 * <p>Mirrors the frontend {@code Prescription} type. The structured medication list
 * is not stored in the current schema (prescription content is held as an encrypted
 * blob), so {@link #medications()} is returned empty and the encrypted content is
 * never decrypted or exposed here.
 */
public record PatientPrescriptionResponse(
        String id,
        String consultationId,
        String patientId,
        String medicId,
        Instant issuedAt,
        Instant expiresAt,
        List<MedicationResponse> medications,
        String diagnosis,
        String notes,
        String signatureUrl
) {
    public record MedicationResponse(
            String id,
            String name,
            String dosage,
            String unit,
            String frequency,
            int durationDays,
            String instructions,
            String interactionWarning
    ) {}
}
