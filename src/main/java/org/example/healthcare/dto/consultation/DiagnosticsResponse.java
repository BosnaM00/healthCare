package org.example.healthcare.dto.consultation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response body for {@code GET /api/v1/consultations/{id}/diagnostics}.
 *
 * <p>Aggregates video session events and heartbeat samples to give medics and
 * admins a full timeline of what happened during a consultation. Used primarily
 * in dispute resolution to establish whether the medic or patient was present
 * and for how long.
 *
 * @param consultationId    Consultation identifier.
 * @param events            Chronologically ordered list of all video session events.
 * @param firstJoinedAt     Earliest participant.joined webhook timestamp.
 * @param lastLeftAt        Latest participant.left webhook timestamp.
 * @param heartbeatCount    Total number of heartbeat samples received.
 */
public record DiagnosticsResponse(
        UUID consultationId,
        List<EventSummary> events,
        Instant firstJoinedAt,
        Instant lastLeftAt,
        long heartbeatCount
) {

    /**
     * Summary of a single video session event.
     *
     * @param eventType   Event type string, e.g. "meeting.started".
     * @param actorUserId Platform user involved in the event — may be null.
     * @param occurredAt  Timestamp of when the event occurred.
     * @param payload     Raw JSON payload from the webhook or heartbeat.
     */
    public record EventSummary(
            String eventType,
            UUID actorUserId,
            Instant occurredAt,
            String payload
    ) {}
}
