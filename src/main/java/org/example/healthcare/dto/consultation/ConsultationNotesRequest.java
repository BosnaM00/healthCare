package org.example.healthcare.dto.consultation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request to save / update medic's notes on a completed consultation. */
public record ConsultationNotesRequest(
        @NotBlank @Size(max = 10_000) String notes
) {}
