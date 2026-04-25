package org.example.healthcare.dto.dispute;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record DisputeRequest(
        @NotNull UUID consultationId,
        @NotBlank @Size(max = 2000) String reason
) {}
