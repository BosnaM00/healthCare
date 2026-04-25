package org.example.healthcare.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Links a patient (User with PATIENT role) to a medic's Slot.
 *
 * <p>consultation_type: SCHEDULED = pre-booked slot | INSTANT = on-demand session
 * <p>payment_status mirrors the Stripe payment-intent lifecycle.
 * <p>cancellation_policy_accepted_at is stored as chargeback evidence.
 */
@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The patient making the booking — references users.id */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private User patient;

    /** The medic being booked — denormalised for query convenience */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medic_id", nullable = false)
    private Medic medic;

    /** The specific time slot being reserved — 1:1; null not allowed */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id", nullable = false, unique = true)
    private Slot slot;

    @Enumerated(EnumType.STRING)
    @Column(name = "consultation_type", nullable = false, length = 15)
    private ConsultationType consultationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 15)
    @Builder.Default
    private BookingPaymentStatus paymentStatus = BookingPaymentStatus.PENDING;

    /** Timestamp at which the patient accepted the cancellation policy — required for GDPR evidence */
    @Column(name = "cancellation_policy_accepted_at")
    private Instant cancellationPolicyAcceptedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
