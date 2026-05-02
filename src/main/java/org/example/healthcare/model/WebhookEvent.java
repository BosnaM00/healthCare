package org.example.healthcare.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Idempotency ledger for incoming Stripe webhook events.
 *
 * <p>Every webhook delivery is persisted here <em>before</em> the handler runs.
 * If a row already exists with the same {@code stripeEventId} and status PROCESSED,
 * the controller returns 200 immediately without re-executing the handler —
 * satisfying Stripe's at-least-once delivery guarantee.
 *
 * <p>The full event payload is intentionally <strong>not</strong> stored here;
 * only the event id and type are retained for auditing. Storing full payloads
 * risks capturing PAN-like patterns from metadata.
 *
 * <p>PK is the Stripe event id (evt_…) — unique by design so the UNIQUE constraint
 * doubles as the idempotency gate.
 */
@Entity
@Table(
        name = "webhook_events",
        indexes = {
                @Index(name = "idx_webhook_events_type",       columnList = "type"),
                @Index(name = "idx_webhook_events_received_at", columnList = "received_at"),
                @Index(name = "idx_webhook_events_status",     columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookEvent {

    /** Stripe event ID (evt_…) — natural unique key */
    @Id
    @Column(name = "stripe_event_id", nullable = false)
    private String stripeEventId;

    /** Stripe event type (e.g. payment_intent.succeeded) */
    @Column(nullable = false, length = 100)
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Builder.Default
    private WebhookEventStatus status = WebhookEventStatus.PENDING;

    /**
     * Source system that sent the event — "stripe" or "daily".
     * Added in V4 migration to keep idempotency clean across providers.
     */
    @Column(nullable = false, length = 16)
    @Builder.Default
    private String source = "stripe";

    /** Number of processing attempts (incremented on each retry) */
    @Column(nullable = false)
    @Builder.Default
    private int attempts = 0;

    /** Last error message if status = FAILED */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;
}
