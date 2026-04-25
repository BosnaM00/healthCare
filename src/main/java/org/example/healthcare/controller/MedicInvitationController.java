package org.example.healthcare.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.healthcare.dto.invitation.MedicInvitationRequest;
import org.example.healthcare.dto.invitation.MedicInvitationResponse;
import org.example.healthcare.service.MedicInvitationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Medic invitation management scoped to a clinic.
 *
 * <p>Role guards:
 * POST   → CLINIC_MANAGER (own clinic only)
 * GET    → CLINIC_MANAGER (own clinic only)
 * DELETE → CLINIC_MANAGER (own clinic only)
 * PUT    accept → MEDIC (consuming their own token)
 */
@RestController
@RequiredArgsConstructor
public class MedicInvitationController {

    private final MedicInvitationService invitationService;

    /** CLINIC_MANAGER — invite a medic by email */
    @PostMapping("/api/v1/clinics/{clinicId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public MedicInvitationResponse invite(@PathVariable UUID clinicId,
                                          @Valid @RequestBody MedicInvitationRequest request) {
        return invitationService.invite(clinicId, request);
    }

    /** CLINIC_MANAGER — list pending invitations for own clinic */
    @GetMapping("/api/v1/clinics/{clinicId}/invitations")
    public List<MedicInvitationResponse> listByClinic(@PathVariable UUID clinicId) {
        return invitationService.listByClinic(clinicId);
    }

    /** CLINIC_MANAGER — revoke a pending invitation */
    @DeleteMapping("/api/v1/clinics/{clinicId}/invitations/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID clinicId,
                       @PathVariable UUID invitationId) {
        invitationService.revoke(invitationId, clinicId);
    }

    /**
     * MEDIC — accept an invitation using the email token.
     * Called during or after medic registration.
     */
    @PutMapping("/api/v1/invitations/accept")
    public MedicInvitationResponse accept(@RequestParam String token,
                                          @RequestParam UUID medicId) {
        return invitationService.accept(token, medicId);
    }
}
