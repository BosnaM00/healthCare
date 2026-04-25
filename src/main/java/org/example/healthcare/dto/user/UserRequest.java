package org.example.healthcare.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.example.healthcare.model.UserRole;

public record UserRequest(
        @Email @NotBlank String email,
        @NotBlank String passwordHash,
        @NotNull UserRole role,
        String stripeCustomerId   // nullable — patients only
) {}
