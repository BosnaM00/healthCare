package org.example.healthcare.repository;

import org.example.healthcare.model.Availability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AvailabilityRepository extends JpaRepository<Availability, UUID> {

    List<Availability> findByMedicId(UUID medicId);

    List<Availability> findByMedicIdAndDayOfWeek(UUID medicId, DayOfWeek dayOfWeek);

    /** Returns true if [start, end) overlaps any existing window for the same medic/day, optionally excluding one row (for updates). */
    @Query("""
            SELECT COUNT(a) > 0 FROM Availability a
            WHERE a.medic.id = :medicId
              AND a.dayOfWeek = :day
              AND a.startTime < :end
              AND a.endTime   > :start
              AND (:excludeId IS NULL OR a.id != :excludeId)
            """)
    boolean existsOverlap(UUID medicId, DayOfWeek day, LocalTime start, LocalTime end, UUID excludeId);
}
