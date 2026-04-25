package org.example.healthcare.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "medic_specialties")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MedicSpecialty {

    @EmbeddedId
    private MedicSpecialtyId id;

    // Owning side — maps medicId part of the composite PK to the medics FK
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("medicId")
    @JoinColumn(name = "medic_id")
    private Medic medic;

    // Owning side — maps specialtyId part of the composite PK to the specialties FK
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("specialtyId")
    @JoinColumn(name = "specialty_id")
    private Specialty specialty;
}
