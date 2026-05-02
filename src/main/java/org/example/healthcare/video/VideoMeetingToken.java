package org.example.healthcare.video;

import java.time.Instant;

/**
 * Immutable value object returned by {@link VideoProvider#issueToken}.
 *
 * <p>Tokens are short-lived (default 15 min), scoped to one room, and bound
 * to a single participant. They must never be written to a persistent log or
 * returned in long-lived DTOs — only delivered once via the {@code /join} endpoint.
 *
 * @param token     Opaque JWT issued by the video provider.
 * @param jti       Claim ID for revocation lookups.
 * @param role      {@link ParticipantRole#OWNER} for the medic, PARTICIPANT for the patient.
 * @param expiresAt Instant after which the token is no longer valid.
 */
public record VideoMeetingToken(
        String token,
        String jti,
        ParticipantRole role,
        Instant expiresAt
) {}
