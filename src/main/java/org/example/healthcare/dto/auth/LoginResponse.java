package org.example.healthcare.dto.auth;

import org.example.healthcare.model.UserRole;

import java.util.UUID;

public record LoginResponse(
        String token,
        UUID userId,
        UserRole role,
        String email,
        String firstName,
        String lastName
) {}
