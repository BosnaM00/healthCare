package org.example.healthcare.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit trail for all medical data access and state transitions.
 *
 * <p>Rules:
 * <ul>
 *   <li>NEVER UPDATE or DELETE rows — Immutable ensures Hibernate never issues UPDATE.</li>
 *   <li>Written directly from services, never exposed via a write API.</li>
 *   <li>Retained minimum 5 years (enforced at infrastructure / lifecycle policy level).</li>
 *   <li>old_value / new_value stored as JSON strings for human readability.</li>
 * </ul>
 *
 * <p>actor_id references users.id — may be null for system-initiated actions (Quartz jobs).
 */
@Entity
@Immutable
@Table(
        name = "audit_logs",
        indexes = {
                @Index(name = "idx_audit_logs_actor_id", columnList = "actor_id"),
                @Index(name = "idx_audit_logs_entity", columnList = "entity_type, entity_id"),
                @Index(name = "idx_audit_logs_created_at", columnList = "created_at")
        }
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * User who triggered the action.
     * Null for system-initiated events (e.g. PaymentReleaseJob).
     */
    @Column(name = "actor_id")
    private UUID actorId;

    /** Verb describing the event (e.g. "BOOKING_CREATED", "PAYMENT_RELEASED") */
    @Column(nullable = false, length = 80)
    private String action;

    /** Simple class name of the affected entity (e.g. "Booking", "Payment") */
    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    /** PK of the affected entity row */
    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    /** JSON snapshot of the entity before the change — null for CREATE events */
    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    /** JSON snapshot of the entity after the change — null for DELETE events */
    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    /** Originating IP address — for GDPR access-log traceability */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
