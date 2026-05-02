package org.example.healthcare.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only record of a single video-related event for a consultation.
 *
 * <p>Sources:
 * <ul>
 *   <li>Daily.co webhook events (meeting.started, participant.joined, etc.)</li>
 *   <li>Client heartbeat samples from {@code POST /consultations/{id}/heartbeat}</li>
 * </ul>
 *
 * <p>Used by the diagnostics endpoint ({@code GET /consultations/{id}/diagnostics})
 * and the dispute UI to reconstruct exactly what happened during a call.
 * Cascade-deleted with the parent consultation (ON DELETE CASCADE in V4 migration).
 */
@Entity
@Table(
        name = "video_session_events",
        indexes = @Index(name = "ix_vse_consultation", columnList = "consultation_id, occurred_at")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VideoSessionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consultation_id", nullable = false)
    private Consultation consultation;

    /**
     * Event type string, e.g. {@code "meeting.started"}, {@code "participant.joined"},
     * {@code "heartbeat"}. Mirrors {@link org.example.healthcare.video.VideoWebhookEventType#getDailyEventType()}.
     */
    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    /**
     * The platform user involved in the event — null for meeting-level events
     * where no specific user is named.
     */
    @Column(name = "actor_user_id")
    private UUID actorUserId;

    /**
     * Raw JSON payload from the webhook or heartbeat body.
     * Stored as JSONB for efficient querying in the diagnostics endpoint.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    /** Timestamp of when the event actually occurred (from the provider or client clock). */
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    /** Timestamp of when we received the event. */
    @CreationTimestamp
    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;
}
