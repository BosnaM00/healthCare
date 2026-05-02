package org.example.healthcare.repository;

import org.example.healthcare.model.VideoSessionEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface VideoSessionEventRepository extends JpaRepository<VideoSessionEvent, UUID> {

    /** All events for a consultation ordered chronologically — used by diagnostics endpoint. */
    List<VideoSessionEvent> findByConsultationIdOrderByOccurredAtAsc(UUID consultationId);

    /** Events of a specific type for a consultation (e.g. all "participant.joined" events). */
    List<VideoSessionEvent> findByConsultationIdAndEventType(UUID consultationId, String eventType);

    /** Checks if any participant.joined event exists for a user in a consultation. */
    @Query("SELECT COUNT(e) > 0 FROM VideoSessionEvent e " +
           "WHERE e.consultation.id = :consultationId " +
           "AND e.eventType = :eventType " +
           "AND e.actorUserId = :userId")
    boolean existsByConsultationIdAndEventTypeAndActorUserId(
            UUID consultationId, String eventType, UUID userId);
}
