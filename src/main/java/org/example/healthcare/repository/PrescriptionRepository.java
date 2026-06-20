package org.example.healthcare.repository;

import org.example.healthcare.model.Prescription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PrescriptionRepository extends JpaRepository<Prescription, UUID> {

    Optional<Prescription> findByConsultationId(UUID consultationId);

    /** All prescriptions belonging to the given patient (booking.patient.id), newest first */
    List<Prescription> findByConsultation_Booking_Patient_IdOrderByCreatedAtDesc(UUID patientUserId);
}
