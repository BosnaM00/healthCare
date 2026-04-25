package org.example.healthcare.service;

import org.example.healthcare.dto.invitation.MedicInvitationRequest;
import org.example.healthcare.dto.invitation.MedicInvitationResponse;

import java.util.List;
import java.util.UUID;

public interface MedicInvitationService {

    /** Clinic manager sends an invite to an email address */
    MedicInvitationResponse invite(UUID clinicId, MedicInvitationRequest request);

    List<MedicInvitationResponse> listByClinic(UUID clinicId);

    /**
     * Medic accepts an invitation by presenting its token during registration.
     * Sets medic.clinic and transitions invitation → ACCEPTED.
     */
    MedicInvitationResponse accept(String token, UUID medicId);

    /** Admin or manager revokes a PENDING invitation */
    void revoke(UUID invitationId, UUID requestingClinicId);

    /** Called by scheduler to bulk-expire tokens past their 72-hour TTL */
    int expireStale();
}
