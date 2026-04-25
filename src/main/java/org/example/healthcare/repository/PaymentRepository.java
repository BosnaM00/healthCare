package org.example.healthcare.repository;

import org.example.healthcare.model.Payment;
import org.example.healthcare.model.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByBookingId(UUID bookingId);

    Optional<Payment> findByStripePaymentIntentId(String stripePaymentIntentId);

    List<Payment> findByStatus(PaymentStatus status);

    @Query("SELECT p FROM Payment p JOIN p.booking b WHERE b.patient.id = :patientId")
    Page<Payment> findByPatientId(UUID patientId, Pageable pageable);

    @Query("SELECT p FROM Payment p JOIN p.booking b WHERE b.medic.id = :medicId")
    Page<Payment> findByMedicId(UUID medicId, Pageable pageable);
}
