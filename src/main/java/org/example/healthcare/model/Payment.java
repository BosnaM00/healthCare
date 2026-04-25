package org.example.healthcare.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Stripe escrow payment record, one per Booking.
 *
 * <p>State machine (mirrors PaymentStatus):
 * <pre>
 *   RESERVED ──► HELD ──► RELEASED   (normal happy path)
 *                 │
 *                 ├──► DISPUTED ──► RELEASED   (dispute resolved for medic)
 *                 │             └──► REFUNDED  (dispute resolved for patient)
 *                 │
 *   RESERVED ──► REFUNDED  (booking cancelled before consultation)
 * </pre>
 *
 * <p>amount          — total charged to patient in smallest currency unit (bani for RON, cents for EUR).
 * <p>platform_fee    — cut retained by MediConnect; calculated as amount × clinic.commission_rate.
 * <p>stripe_payment_intent_id — Stripe PI used for capture / cancel.
 * <p>stripe_transfer_id       — Stripe Transfer used when releasing funds to medic/clinic.
 */
@Entity
@Table(
        name = "payments",
        indexes = {
                @Index(name = "idx_payments_status", columnList = "status"),
                @Index(name = "idx_payments_booking_id", columnList = "booking_id")
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

    /** Stripe PaymentIntent ID — used to capture / cancel the escrow */
    @Column(name = "stripe_payment_intent_id", nullable = false)
    private String stripePaymentIntentId;

    /**
     * Stripe Transfer ID — populated when funds are released to the medic's
     * or clinic's Stripe Connect account.
     */
    @Column(name = "stripe_transfer_id")
    private String stripeTransferId;

    /** Total amount charged to patient in smallest currency unit (bani / cents) */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /**
     * Platform fee retained by MediConnect.
     * Calculated as: amount × medic.clinic.commission_rate (or default rate for independents).
     */
    @Column(name = "platform_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal platformFee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.RESERVED;

    /** Timestamp when funds were transferred to the medic/clinic account */
    @Column(name = "released_at")
    private Instant releasedAt;

    /** Timestamp when funds were returned to the patient */
    @Column(name = "refunded_at")
    private Instant refundedAt;
}
