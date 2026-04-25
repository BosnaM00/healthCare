package org.example.healthcare.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Recurring weekly template that defines when a medic is available.
 * A Quartz job reads these records and materialises concrete {@link Slot} rows
 * for the upcoming scheduling window.
 */
@Entity
@Table(name = "availabilities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Availability {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medic_id", nullable = false)
    private Medic medic;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 15)
    private DayOfWeek dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    /** Duration of each generated slot in minutes (e.g. 30). */
    @Column(name = "slot_duration_min", nullable = false)
    private int slotDurationMin;

    /** Buffer between consecutive slots in minutes (e.g. 5). */
    @Column(name = "buffer_min", nullable = false)
    @Builder.Default
    private int bufferMin = 0;
}
