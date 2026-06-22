package org.example.healthcare.repository;

import org.example.healthcare.model.Prescription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PrescriptionRepository extends JpaRepository<Prescription, UUID> {

    Optional<Prescription> findByConsultationId(UUID consultationId);

    /** All prescriptions belonging to the given patient (booking.patient.id), newest first */
    List<Prescription> findByConsultation_Booking_Patient_IdOrderByCreatedAtDesc(UUID patientUserId);

    /**
     * Prescriptions issued by a medic for a given patient, newest first — medic patient-record view.
     *
     * <p>Uses JOIN FETCH for consultation/booking/patient/medic because the controller maps these
     * outside a transaction; a plain JOIN would leave them lazy and trigger a
     * LazyInitializationException when the response mapper navigates them.
     */
    @Query("""
            SELECT p FROM Prescription p
            JOIN FETCH p.consultation c
            JOIN FETCH c.booking b
            JOIN FETCH b.patient
            JOIN FETCH b.medic
            WHERE b.medic.id = :medicId
              AND b.patient.id = :patientId
            ORDER BY p.createdAt DESC
            """)
    List<Prescription> findByMedicIdAndPatientId(@Param("medicId") UUID medicId,
                                                 @Param("patientId") UUID patientId);
}
