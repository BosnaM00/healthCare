package org.example.healthcare.video;

import java.time.Instant;

/**
 * Immutable value object returned by {@link VideoProvider#createRoom}.
 *
 * @param providerName Short identifier of the provider, e.g. "daily".
 * @param roomName     Provider-internal room name (used for token issuance and deletion).
 * @param roomUrl      Full join URL to be shared with participants.
 * @param expiresAt    Instant after which the room is automatically deleted by the provider.
 */
public record VideoRoom(
        String providerName,
        String roomName,
        String roomUrl,
        Instant expiresAt
) {}
