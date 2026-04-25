package org.example.healthcare.dto.clinic;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record ClinicRequest(
        @NotBlank String name,

        /** Romanian business registration number */
        @NotBlank @Size(max = 20) String cui,

        /**
         * Platform commission rate as a decimal fraction (0.01 – 0.50).
         * Defaults to 0.15 if omitted; admin may override at any time.
         */
        @DecimalMin("0.01") @DecimalMax("0.50") BigDecimal commissionRate
) {}
