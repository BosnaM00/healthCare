package org.example.healthcare.repository;

import org.example.healthcare.model.Clinic;
import org.example.healthcare.model.ClinicStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClinicRepository extends JpaRepository<Clinic, UUID> {

    boolean existsByCui(String cui);

    Optional<Clinic> findByCui(String cui);

    List<Clinic> findByStatus(ClinicStatus status);
}
