package org.example.healthcare.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Stripe escrow payment record, one per Booking.
 *
 * <p>State machine (mirrors {@link PaymentStatus}):
 * <pre>
 *   RESERVED ──► HELD ──► RELEASED   (normal happy path)
 *                │
 *                ├──► DISPUTED ──► RELEASED   (dispute resolved for medic)
 *                │             └──► REFUNDED  (dispute resolved for patient)
 *                │
 *   RESERVED ──► REFUNDED  (booking cancelled before consultation)
 *   RESERVED ──► FAILED    (Stripe PI failed / cancelled)
 * </pre>
 *
 * <p>Money fields:
 * <ul>
 *   <li>{@code amount} / {@code amountBani} — total charged to patient (BigDecimal RON / long bani).</li>
 *   <li>{@code platformFee} / {@code applicationFeeBani} — MediConnect cut (BigDecimal RON / long bani).</li>
 * </ul>
 *
 * <p>Idempotency: {@code version} provides optimistic locking so concurrent release/refund
 * attempts surface as {@link jakarta.persistence.OptimisticLockException}.
 */
@Entity
@Table(
        name = "payments",
        indexes = {
                @Index(name = "idx_payments_status",                   columnList = "status"),
                @Index(name = "idx_payments_booking_id",               columnList = "booking_id"),
                @Index(name = "idx_payments_stripe_pi_id",             columnList = "stripe_payment_intent_id"),
                @Index(name = "idx_payments_state_updated_at",         columnList = "status, updated_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** 1:1 — exactly one payment per booking */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    // ── Denormalised foreign keys for fast query without JOIN ─────────────────

    /** Patient user ID — denormalised from booking.patient.id */
    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    /** Medic ID — denormalised from booking.medic.id */
    @Column(name = "medic_id", nullable = false)
    private UUID medicId;

    // ── Stripe identifiers ────────────────────────────────────────────────────

    /** Stripe PaymentIntent ID (pi_…) — used to capture/cancel */
    @Column(name = "stripe_payment_intent_id", nullable = false)
    private String stripePaymentIntentId;

    /** Stripe Charge ID (ch_…) — populated on payment_intent.succeeded webhook */
    @Column(name = "stripe_charge_id")
    private String stripeChargeId;

    /**
     * Stripe Transfer ID (tr_…) — populated when funds are released to
     * the medic's or clinic's Stripe Connect account.
     */
    @Column(name = "stripe_transfer_id")
    private String stripeTransferId;

    /** Stripe Refund ID (re_…) — populated after refund is issued */
    @Column(name = "stripe_refund_id")
    private String stripeRefundId;

    /** Stripe Dispute ID (dp_…) — populated on charge.dispute.created webhook */
    @Column(name = "stripe_dispute_id")
    private String stripeDisputeId;

    // ── Money fields ──────────────────────────────────────────────────────────

    /**
     * Total amount charged to patient in RON (major units, 2 decimal places).
     * Retained for human-readable display alongside the canonical {@code amountBani}.
     */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /**
     * Total amount in bani (minor units, 1 RON = 100 bani).
     * Canonical Stripe value — always use this when calling the Stripe SDK.
     */
    @Column(name = "amount_bani", nullable = false)
    private long amountBani;

    /**
     * Platform fee retained by MediConnect in RON.
     * Calculated as: amount × commission_rate.
     */
    @Column(name = "platform_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal platformFee;

    /**
     * Platform fee in bani (minor units).
     * Passed as {@code application_fee_amount} to Stripe PaymentIntent.
     */
    @Column(name = "application_fee_bani", nullable = false)
    private long applicationFeeBani;

    /** ISO 4217 currency code — always 'RON' for this platform */
    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "RON";

    // ── State ─────────────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.RESERVED;

    // ── Timestamps ────────────────────────────────────────────────────────────

    /** Timestamp when funds were transferred to the medic/clinic account */
    @Column(name = "released_at")
    private Instant releasedAt;

    /** Timestamp when funds were returned to the patient */
    @Column(name = "refunded_at")
    private Instant refundedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ── Optimistic locking ────────────────────────────────────────────────────

    /**
     * JPA optimistic lock version.
     * Concurrent release/refund attempts will throw {@link jakarta.persistence.OptimisticLockException}
     * on the second committer, which surfaces as HTTP 409 to the caller.
     */
    @Version
    private Long version;

    // ── Transient ─────────────────────────────────────────────────────────────

    /**
     * Stripe PaymentIntent client_secret returned to the frontend for Stripe.js confirmation.
     * Never persisted — populated by service layer after PaymentIntent creation.
     */
    @Transient
    private String clientSecret;
}
