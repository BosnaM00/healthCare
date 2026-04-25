package org.example.healthcare.repository;

import org.example.healthcare.model.Booking;
import org.example.healthcare.model.BookingPaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {

    Page<Booking> findByPatientId(UUID patientId, Pageable pageable);

    Page<Booking> findByMedicId(UUID medicId, Pageable pageable);

    List<Booking> findByPatientIdAndPaymentStatus(UUID patientId, BookingPaymentStatus paymentStatus);

    Optional<Booking> findBySlotId(UUID slotId);
}
