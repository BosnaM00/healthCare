package org.example.healthcare.dto.medic;

import java.time.Instant;
import java.util.List;

/**
 * Patient summary shown on the medic's "Patient record" page.
 *
 * <p>Fields mirror the frontend {@code PatientDetail} type. Clinical fields that
 * have no backing column in the current schema (date of birth, blood type,
 * allergies, insurer, active conditions) are returned empty/null rather than
 * fabricated — the UI degrades gracefully when they are absent.
 */
public record PatientDetailResponse(
        String id,
        String userId,
        String firstName,
        String lastName,
        String dateOfBirth,
        String bloodType,
        List<String> allergies,
        String email,
        String phone,
        String avatarUrl,
        String insurerName,
        String insurancePolicyNumber,
        Instant lastConsultationAt,
        Instant nextBookingAt,
        List<String> activeConditions
) {}
