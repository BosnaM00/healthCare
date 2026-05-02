package org.example.healthcare.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Persistent audit record for every meeting token issued via
 * {@code POST /api/v1/consultations/{id}/join}.
 *
 * <p>Serves two purposes:
 * <ol>
 *   <li><strong>Audit</strong> — satisfies Romanian healthcare audit requirements
 *       and GDPR Article 9 token-issuance logging.</li>
 *   <li><strong>Revocation</strong> — the {@code tokenJti} column can be checked
 *       against an in-memory or Redis set to reject replayed tokens.</li>
 * </ol>
 *
 * <p>The actual opaque token string is <strong>never stored here</strong> — only the JTI,
 * role, and validity window are persisted.
 */
@Entity
@Table(
        name = "video_meeting_tokens",
        indexes = @Index(name = "ix_vmt_consultation", columnList = "consultation_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetingTokenRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consultation_id", nullable = false)
    private Consultation consultation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** OWNER (medic) or PARTICIPANT (patient). */
    @Column(nullable = false, length = 16)
    private String role;

    /** JTI claim attached to the token — unique; used for revocation. */
    @Column(name = "token_jti", nullable = false, unique = true, length = 64)
    private String tokenJti;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Set when the token is explicitly revoked before natural expiry. */
    @Column(name = "revoked_at")
    private Instant revokedAt;
}
