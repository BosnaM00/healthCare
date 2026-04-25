package org.example.healthcare.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Live video consultation derived from a confirmed Booking.
 *
 * <p>notes_encrypted — consultation notes written by the medic, AES-256 GCM encrypted.
 * The encryption key lives in AWS KMS; EncryptionService handles wrap/unwrap.
 *
 * <p>release_at — timestamp after which the Quartz PaymentReleaseJob moves the
 * linked Payment from HELD → RELEASED. Calculated as ended_at + 48h (dispute window).
 *
 * <p>State transitions:
 * SCHEDULED → IN_PROGRESS (medic opens room) → COMPLETED (both leave) → [normal]
 *                                             → FAILED (timeout / no-show)
 * Any COMPLETED consultation can be moved to DISPUTED by the DisputeService.
 */
@Entity
@Table(
        name = "consultations",
        indexes = @Index(name = "idx_consultations_release_at", columnList = "release_at")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Consultation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** 1:1 — every booking produces exactly one consultation record */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ConsultationStatus status = ConsultationStatus.SCHEDULED;

    /** Daily.co / Twilio room name — generated at booking time */
    @Column(name = "video_room_id")
    private String videoRoomId;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    /** Elapsed session time in seconds; set when the room closes */
    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    /**
     * AES-256 GCM ciphertext of the medic's consultation notes.
     * Format: base64(iv || ciphertext || authTag).
     * Decryption: EncryptionService.decrypt(notesEncrypted).
     */
    @Column(name = "notes_encrypted", columnDefinition = "TEXT")
    private String notesEncrypted;

    /**
     * Earliest instant at which the Payment may be released to the medic.
     * Set to ended_at + 48h when status transitions to COMPLETED.
     * The PaymentReleaseJob polls this column.
     */
    @Column(name = "release_at")
    private Instant releaseAt;

    @OneToOne(mappedBy = "consultation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Prescription prescription;

    @OneToMany(mappedBy = "consultation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Dispute> disputes = new ArrayList<>();
}
