package org.example.healthcare.repository;

import org.example.healthcare.model.Prescription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PrescriptionRepository extends JpaRepository<Prescription, UUID> {

    Optional<Prescription> findByConsultationId(UUID consultationId);
}
