package org.example.healthcare.repository;

import org.example.healthcare.model.Booking;
import org.example.healthcare.model.BookingPaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {

    Page<Booking> findByPatientId(UUID patientId, Pageable pageable);

    Page<Booking> findByMedicId(UUID medicId, Pageable pageable);

    List<Booking> findByPatientIdAndPaymentStatus(UUID patientId, BookingPaymentStatus paymentStatus);

    Optional<Booking> findBySlotId(UUID slotId);

    /** Upcoming bookings for the given medic — slot starts in the future, not refunded. */
    @Query("""
            SELECT b FROM Booking b
            JOIN FETCH b.slot s
            JOIN FETCH b.patient p
            JOIN FETCH b.medic m
            JOIN FETCH m.user
            WHERE b.medic.id = :medicId
              AND s.startsAt > :now
              AND b.paymentStatus != org.example.healthcare.model.BookingPaymentStatus.REFUNDED
            ORDER BY s.startsAt ASC
            """)
    List<Booking> findUpcomingByMedicId(@Param("medicId") UUID medicId, @Param("now") Instant now);
}
