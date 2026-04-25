package org.example.healthcare.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class MedicSpecialtyId implements Serializable {

    @Column(name = "medic_id")
    private UUID medicId;

    @Column(name = "specialty_id")
    private UUID specialtyId;
}
