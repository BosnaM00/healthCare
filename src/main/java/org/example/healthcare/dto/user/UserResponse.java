package org.example.healthcare.dto.user;

import org.example.healthcare.model.UserRole;
import org.example.healthcare.model.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        UserRole role,
        UserStatus status,
        String stripeCustomerId,
        Instant createdAt
) {}
