package org.example.healthcare.repository;

import org.example.healthcare.model.WebhookEvent;
import org.example.healthcare.model.WebhookEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, String> {

    boolean existsByStripeEventId(String stripeEventId);

    /**
     * Returns the most recent received_at timestamp for each event type.
     * Used by the webhook health endpoint.
     */
    @Query("SELECT w.type, MAX(w.receivedAt) FROM WebhookEvent w GROUP BY w.type ORDER BY w.type")
    List<Object[]> findLatestReceivedAtByType();

    List<WebhookEvent> findByStatusAndReceivedAtBefore(WebhookEventStatus status, Instant before);
}
