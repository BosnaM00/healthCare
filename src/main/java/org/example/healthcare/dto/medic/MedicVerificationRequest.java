package org.example.healthcare.dto.medic;

import jakarta.validation.constraints.NotNull;
import org.example.healthcare.model.VerificationStatus;

public record MedicVerificationRequest(@NotNull VerificationStatus status) {}
