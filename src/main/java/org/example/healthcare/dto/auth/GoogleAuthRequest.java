package org.example.healthcare.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /api/v1/auth/google.
 * The frontend obtains the ID token from Google Sign-In and passes it here
 * for backend verification.
 */
public record GoogleAuthRequest(
        @NotBlank(message = "Google ID token must not be blank")
        String idToken
) {}
