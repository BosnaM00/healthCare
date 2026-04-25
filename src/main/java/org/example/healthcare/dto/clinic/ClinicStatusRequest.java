package org.example.healthcare.dto.clinic;

import jakarta.validation.constraints.NotNull;
import org.example.healthcare.model.ClinicStatus;

public record ClinicStatusRequest(@NotNull ClinicStatus status) {}
