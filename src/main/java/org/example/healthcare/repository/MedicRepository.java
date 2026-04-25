package org.example.healthcare.repository;

import org.example.healthcare.model.Medic;
import org.example.healthcare.model.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MedicRepository extends JpaRepository<Medic, UUID>, JpaSpecificationExecutor<Medic> {

    Optional<Medic> findByUserId(UUID userId);

    Optional<Medic> findByLicenseNumber(String licenseNumber);

    List<Medic> findByVerificationStatus(VerificationStatus status);

    List<Medic> findByAvailableForInstantTrue();

    /** All active medics that hold a given specialty. */
    @Query("""
            SELECT m FROM Medic m
            JOIN m.medicSpecialties ms
            WHERE ms.specialty.id = :specialtyId
              AND m.verificationStatus = 'ACTIVE'
            """)
    List<Medic> findActiveBySpecialtyId(UUID specialtyId);
}
