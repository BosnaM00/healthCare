package org.example.healthcare.repository;

import org.example.healthcare.model.MedicSpecialty;
import org.example.healthcare.model.MedicSpecialtyId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MedicSpecialtyRepository extends JpaRepository<MedicSpecialty, MedicSpecialtyId> {

    List<MedicSpecialty> findByMedicId(UUID medicId);

    List<MedicSpecialty> findBySpecialtyId(UUID specialtyId);

    boolean existsByMedicIdAndSpecialtyId(UUID medicId, UUID specialtyId);

    boolean existsBySpecialtyId(UUID specialtyId);
}
