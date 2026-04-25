package org.example.healthcare.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A concrete, bookable time window generated from an {@link Availability} template.
 * Status transitions: AVAILABLE → BOOKED (on booking) | BLOCKED (manually or by system).
 */
@Entity
@Table(name = "slots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Slot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medic_id", nullable = false)
    private Medic medic;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Builder.Default
    private SlotStatus status = SlotStatus.AVAILABLE;

    // inverse side — FK lives on bookings.slot_id
    @OneToOne(mappedBy = "slot", fetch = FetchType.LAZY)
    private Booking booking;
}
