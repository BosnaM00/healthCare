package org.example.healthcare.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Clinic-to-medic invite token.
 *
 * <p>Flow: Clinic manager sends invite → email delivered with token link →
 * medic registers using the token → status becomes ACCEPTED, medic.clinic set.
 *
 * <p>Token TTL: 72 hours. A Quartz job (or @Scheduled) expires PENDING rows.
 */
@Entity
@Table(
        name = "medic_invitations",
        indexes = @Index(name = "idx_medic_invitations_token", columnList = "token")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clinic_id", nullable = false)
    private Clinic clinic;

    /** Target email address — not necessarily a registered user yet */
    @Column(nullable = false)
    private String email;

    /** Cryptographically random token — delivered via email link */
    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Builder.Default
    private InvitationStatus status = InvitationStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Set when a medic registers using this token */
    @Column(name = "accepted_at")
    private Instant acceptedAt;

    /** Convenience: token is valid if still PENDING and within 72-hour window */
    public boolean isValid() {
        return status == InvitationStatus.PENDING
                && createdAt.plusSeconds(72 * 3600).isAfter(Instant.now());
    }
}
