package org.example.healthcare.repository;

import org.example.healthcare.model.Payment;
import org.example.healthcare.model.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByBookingId(UUID bookingId);

    Optional<Payment> findByStripePaymentIntentId(String stripePaymentIntentId);

    Optional<Payment> findByStripeChargeId(String stripeChargeId);

    Optional<Payment> findByStripeTransferId(String stripeTransferId);

    List<Payment> findByStatus(PaymentStatus status);

    /** Payments in HELD state whose consultation release_at has passed — used by auto-release job. */
    @Query("""
           SELECT p FROM Payment p
           JOIN Consultation c ON c.booking.id = p.booking.id
           WHERE p.status = org.example.healthcare.model.PaymentStatus.HELD
             AND c.releaseAt <= :now
           """)
    List<Payment> findReleasablePayments(Instant now);

    /** Payments by patient — uses denormalised column for index scan. */
    Page<Payment> findByPatientId(UUID patientId, Pageable pageable);

    /** Payments by medic — uses denormalised column for index scan. */
    Page<Payment> findByMedicId(UUID medicId, Pageable pageable);

    /** Paginated view of all payments for operator console. */
    @Query("SELECT p FROM Payment p ORDER BY p.createdAt DESC")
    Page<Payment> findAllOrderByCreatedAtDesc(Pageable pageable);

    /** Count by status — used by operator dashboard metrics. */
    long countByStatus(PaymentStatus status);

    /** Payments in a given status updated before a given time — used for stale-detection. */
    List<Payment> findByStatusAndUpdatedAtBefore(PaymentStatus status, Instant before);

    /** Earnings for a medic within a date range, filtered by status. */
    List<Payment> findByMedicIdAndCreatedAtBetweenAndStatusIn(
            UUID medicId, Instant start, Instant end, Collection<PaymentStatus> statuses);

    /** Earnings for all medics belonging to a clinic within a date range. */
    @Query("""
           SELECT p FROM Payment p
           WHERE p.medicId IN (
               SELECT m.id FROM Medic m WHERE m.clinic.id = :clinicId
           )
           AND p.createdAt BETWEEN :start AND :end
           AND p.status IN :statuses
           ORDER BY p.createdAt DESC
           """)
    List<Payment> findByClinicIdAndCreatedAtBetweenAndStatusIn(
            @Param("clinicId") UUID clinicId,
            @Param("start") Instant start,
            @Param("end") Instant end,
            @Param("statuses") Collection<PaymentStatus> statuses);
}
