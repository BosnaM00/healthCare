package org.example.healthcare.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "medics",
        uniqueConstraints = @UniqueConstraint(name = "uk_medics_user_id", columnNames = "user_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Medic {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Owning side of User ↔ Medic one-to-one
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /**
     * Nullable — null means independent practitioner.
     * Clinic is introduced in Phase 2; FK nullable so existing independent medics
     * are unaffected. commission_rate for independents uses a platform default.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clinic_id")
    private Clinic clinic;

    @Column(name = "license_number", nullable = false, unique = true)
    private String licenseNumber;

    @Column(name = "license_expires_at")
    private LocalDate licenseExpiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 30)
    @Builder.Default
    private VerificationStatus verificationStatus = VerificationStatus.PENDING_DOCUMENTS;

    @Column(name = "stripe_account_id")
    private String stripeAccountId;

    @Column(name = "is_available_for_instant", nullable = false)
    @Builder.Default
    private boolean availableForInstant = false;

    // Owning collection — cascade keeps junction rows in sync with medic lifecycle
    @OneToMany(mappedBy = "medic", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<MedicSpecialty> medicSpecialties = new ArrayList<>();

    @OneToMany(mappedBy = "medic", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Availability> availabilities = new ArrayList<>();

    @OneToMany(mappedBy = "medic", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Slot> slots = new ArrayList<>();

    @OneToMany(mappedBy = "medic", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Booking> bookings = new ArrayList<>();

    // --- convenience helpers ---

    public void addSpecialty(Specialty specialty) {
        MedicSpecialtyId pk = new MedicSpecialtyId(this.id, specialty.getId());
        MedicSpecialty junction = new MedicSpecialty(pk, this, specialty);
        medicSpecialties.add(junction);
        specialty.getMedicSpecialties().add(junction);
    }

    public void addAvailability(Availability availability) {
        availability.setMedic(this);
        availabilities.add(availability);
    }

    public void addSlot(Slot slot) {
        slot.setMedic(this);
        slots.add(slot);
    }
}
