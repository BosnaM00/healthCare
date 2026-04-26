package org.example.healthcare.model;

/**
 * State machine for payments.status:
 *
 * RESERVED  — Stripe PaymentIntent created; funds captured but not yet transferred.
 *             Set at booking creation.
 * HELD      — Consultation completed; escrow window open (48 h dispute period).
 *             Set when consultation status → COMPLETED.
 * RELEASED  — Funds transferred to medic/clinic Stripe account.
 *             Set by PaymentReleaseJob after release_at passes or admin force-release.
 * DISPUTED  — Patient raised a dispute before release_at.
 *             Set when a Dispute is opened.
 * REFUNDED  — Patient refunded in full or in part after dispute resolution or cancellation.
 *             Set by DisputeService or BookingService.cancel().
 * FAILED    — Stripe PaymentIntent failed or was cancelled before funds were captured.
 *             Set on payment_intent.payment_failed / payment_intent.canceled webhooks.
 */
public enum PaymentStatus {
    RESERVED,
    HELD,
    RELEASED,
    DISPUTED,
    REFUNDED,
    FAILED
}
