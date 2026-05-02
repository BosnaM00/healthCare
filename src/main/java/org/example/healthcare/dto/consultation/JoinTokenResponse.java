package org.example.healthcare.dto.consultation;

import java.time.Instant;

/**
 * Response body for {@code POST /api/v1/consultations/{id}/join}.
 *
 * <p><strong>Security note:</strong> The {@code token} is a short-lived (15 min)
 * meeting token from the video provider. It must never be logged, persisted on
 * the client side, or returned in long-lived caches. The frontend should hold it
 * in component state only and re-issue via {@code /join} on page refresh.
 *
 * @param roomUrl   Full Daily.co join URL.
 * @param token     Opaque provider meeting token — short-lived and single-use.
 * @param role      {@code "OWNER"} for the medic, {@code "PARTICIPANT"} for the patient.
 * @param expiresAt Instant after which the token is no longer valid.
 */
public record JoinTokenResponse(
        String roomUrl,
        String token,
        String role,
        Instant expiresAt
) {}
