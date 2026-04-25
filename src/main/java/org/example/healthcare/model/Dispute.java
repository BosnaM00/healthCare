package org.example.healthcare.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Patient-raised dispute against a completed consultation.
 *
 * <p>Admin SLA: resolve within 48 hours of creation.
 *
 * <p>Resolution outcomes map to DisputeStatus:
 * <ul>
 *   <li>RESOLVED_RELEASED  — medic was not at fault; payment released normally</li>
 *   <li>RESOLVED_REFUNDED  — full patient refund; payment reversed</li>
 *   <li>RESOLVED_PARTIAL   — split decision; PaymentService handles partial transfer + refund</li>
 * </ul>
 *
 * <p>A single consultation may accumulate multiple disputes only in edge cases;
 * guard in service layer against opening a second OPEN dispute on the same consultation.
 */
@Entity
@Table(
        name = "disputes",
        indexes = {
                @Index(name = "idx_disputes_status", columnList = "status"),
                @Index(name = "idx_disputes_consultation_id", columnList = "consultation_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dispute {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consultation_id", nullable = false)
    private Consultation consultation;

    /** Patient or support agent who filed the dispute */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raised_by", nullable = false)
    private User raisedBy;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    @Builder.Default
    private DisputeStatus status = DisputeStatus.OPEN;

    /** Admin who resolved the dispute — null until resolution */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by")
    private User resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    /** Admin's written rationale for the resolution decision */
    @Column(name = "resolution_note", columnDefinition = "TEXT")
    private String resolutionNote;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
