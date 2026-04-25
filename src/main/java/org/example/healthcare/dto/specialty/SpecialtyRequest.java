package org.example.healthcare.dto.specialty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SpecialtyRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description
) {}
