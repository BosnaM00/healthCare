package org.example.healthcare.dto.diagnosis;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Request to infer a likely diagnosis from a set of prescribed medications.
 *
 * <p>The medication list is sent from the client rather than read from storage,
 * so this endpoint stays stateless decision-support: it never touches the
 * encrypted prescription content and produces no persisted side effects.
 */
public record DiagnosisInferenceRequest(
        @NotEmpty(message = "At least one medication is required")
        @Valid
        List<Medication> medications
) {
    public record Medication(
            @NotBlank(message = "Medication name is required")
            String name,
            String dosage,
            String unit,
            String frequency,
            Integer durationDays
    ) {}
}
