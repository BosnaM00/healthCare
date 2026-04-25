package org.example.healthcare.repository;

import org.example.healthcare.model.InvitationStatus;
import org.example.healthcare.model.MedicInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MedicInvitationRepository extends JpaRepository<MedicInvitation, UUID> {

    Optional<MedicInvitation> findByToken(String token);

    List<MedicInvitation> findByClinicIdAndStatus(UUID clinicId, InvitationStatus status);

    boolean existsByEmailAndClinicIdAndStatus(String email, UUID clinicId, InvitationStatus status);

    /**
     * Bulk-expire all PENDING invitations whose 72-hour window has elapsed.
     * Called by the InvitationExpiryJob (or @Scheduled task).
     */
    @Modifying
    @Query("UPDATE MedicInvitation i SET i.status = 'EXPIRED' " +
           "WHERE i.status = 'PENDING' AND i.createdAt < :cutoff")
    int expireBefore(Instant cutoff);
}
