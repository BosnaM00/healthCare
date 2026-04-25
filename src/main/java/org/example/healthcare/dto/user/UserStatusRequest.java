package org.example.healthcare.dto.user;

import jakarta.validation.constraints.NotNull;
import org.example.healthcare.model.UserStatus;

public record UserStatusRequest(@NotNull UserStatus status) {}
