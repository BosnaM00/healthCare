package org.example.healthcare.repository;

import org.example.healthcare.model.MeetingTokenRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MeetingTokenRepository extends JpaRepository<MeetingTokenRecord, UUID> {

    /** Find all tokens issued for a consultation — used for audit queries. */
    List<MeetingTokenRecord> findByConsultationId(UUID consultationId);

    /** Lookup by JTI for revocation checks. */
    Optional<MeetingTokenRecord> findByTokenJti(String tokenJti);

    /** Check if an active (non-revoked, non-expired) token already exists for a user+consultation. */
    boolean existsByConsultationIdAndUserIdAndRevokedAtIsNull(UUID consultationId, UUID userId);
}
