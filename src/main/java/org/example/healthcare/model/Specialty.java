package org.example.healthcare.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "specialties")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Specialty {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    // inverse side — FK lives on medic_specialties.specialty_id
    @OneToMany(mappedBy = "specialty", fetch = FetchType.LAZY)
    @Builder.Default
    private List<MedicSpecialty> medicSpecialties = new ArrayList<>();
}
