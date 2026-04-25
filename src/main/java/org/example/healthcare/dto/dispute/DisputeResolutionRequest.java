package org.example.healthcare.dto.dispute;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.healthcare.model.DisputeStatus;

public record DisputeResolutionRequest(
        /** Must be one of: RESOLVED_RELEASED, RESOLVED_REFUNDED, RESOLVED_PARTIAL */
        @NotNull DisputeStatus resolution,
        @NotBlank @Size(max = 2000) String resolutionNote
) {}
