package org.example.healthcare.dto.consultation;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Request body for {@code POST /api/v1/consultations/{id}/heartbeat}.
 *
 * <p>Sent by the frontend every 30 seconds while a call is active. The backend
 * uses this as a secondary signal alongside Daily webhooks to detect no-show
 * conditions and extend the Quartz no-show window when both parties are connected.
 *
 * @param clientUtcTimestamp Client-side UTC timestamp of the heartbeat.
 * @param networkRttMs       Round-trip latency in milliseconds (from Daily SDK).
 * @param mediaState         Human-readable media state string, e.g. "audio+video", "audio-only".
 */
public record HeartbeatRequest(
        @NotNull Instant clientUtcTimestamp,
        Integer networkRttMs,
        String mediaState
) {}
