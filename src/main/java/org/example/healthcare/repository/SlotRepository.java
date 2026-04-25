package org.example.healthcare.repository;

import jakarta.persistence.LockModeType;
import org.example.healthcare.model.Slot;
import org.example.healthcare.model.SlotStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SlotRepository extends JpaRepository<Slot, UUID> {

    List<Slot> findByMedicIdAndStatus(UUID medicId, SlotStatus status);

    List<Slot> findByMedicIdAndStatusAndStartsAtBetween(UUID medicId, SlotStatus status, Instant from, Instant to);

    List<Slot> findByMedicIdAndStartsAtBetween(UUID medicId, Instant from, Instant to);

    Optional<Slot> findByIdAndMedicId(UUID id, UUID medicId);

    /** Pessimistic write lock — prevents double-booking on concurrent booking requests. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Slot s WHERE s.id = :id")
    Optional<Slot> findByIdWithLock(UUID id);

    /** Available slots for a medic within a time window — used by the booking search flow. */
    @Query("""
            SELECT s FROM Slot s
            WHERE s.medic.id = :medicId
              AND s.status = 'AVAILABLE'
              AND s.startsAt >= :from
              AND s.endsAt   <= :to
            ORDER BY s.startsAt
            """)
    List<Slot> findAvailableByMedicIdAndWindow(UUID medicId, Instant from, Instant to);

    /** All available slots across all medics for a specialty in a time window. */
    @Query("""
            SELECT s FROM Slot s
            JOIN s.medic m
            JOIN m.medicSpecialties ms
            WHERE ms.specialty.id = :specialtyId
              AND s.status = 'AVAILABLE'
              AND s.startsAt >= :from
              AND s.endsAt   <= :to
            ORDER BY s.startsAt
            """)
    List<Slot> findAvailableBySpecialtyAndWindow(UUID specialtyId, Instant from, Instant to);
}
