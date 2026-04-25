package org.example.healthcare.dto.prescription;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PrescriptionRequest(
        @NotBlank @Size(max = 20_000) String content
) {}
