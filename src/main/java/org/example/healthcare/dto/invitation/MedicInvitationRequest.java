package org.example.healthcare.dto.invitation;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record MedicInvitationRequest(
        @Email @NotBlank String email
) {}
