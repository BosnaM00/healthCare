package org.example.healthcare.model;

/**
 * Processing state for a received Stripe webhook event.
 *
 * PENDING    — Event received and persisted; handler has not yet run.
 * PROCESSED  — Handler completed successfully; event is idempotency-safe to ignore on replay.
 * FAILED     — Handler threw an exception; Stripe will retry delivery.
 */
public enum WebhookEventStatus {
    PENDING,
    PROCESSED,
    FAILED
}
