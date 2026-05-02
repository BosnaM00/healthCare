package org.example.healthcare.repository;

import org.example.healthcare.model.Consultation;
import org.example.healthcare.model.ConsultationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConsultationRepository extends JpaRepository<Consultation, UUID> {

    Optional<Consultation> findByBookingId(UUID bookingId);

    /** Used by VideoWebhookController to resolve the consultation from the Daily room name. */
    Optional<Consultation> findByVideoRoomId(String videoRoomId);

    @Query("SELECT c FROM Consultation c JOIN c.booking b WHERE b.patient.id = :patientId")
    Page<Consultation> findByPatientId(UUID patientId, Pageable pageable);

    @Query("SELECT c FROM Consultation c JOIN c.booking b WHERE b.medic.id = :medicId")
    Page<Consultation> findByMedicId(UUID medicId, Pageable pageable);

    /**
     * Finds COMPLETED consultations whose release_at has passed — used by PaymentReleaseJob.
     * Limit applied in the job to process in safe batches.
     */
    @Query("SELECT c FROM Consultation c " +
           "WHERE c.status = 'COMPLETED' " +
           "AND c.releaseAt IS NOT NULL " +
           "AND c.releaseAt <= :now")
    List<Consultation> findReleasable(Instant now);

    List<Consultation> findByStatus(ConsultationStatus status);
}
