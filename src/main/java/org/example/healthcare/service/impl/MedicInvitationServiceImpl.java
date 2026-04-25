package org.example.healthcare.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.healthcare.common.exception.BusinessException;
import org.example.healthcare.common.exception.ResourceNotFoundException;
import org.example.healthcare.dto.invitation.MedicInvitationRequest;
import org.example.healthcare.dto.invitation.MedicInvitationResponse;
import org.example.healthcare.model.Clinic;
import org.example.healthcare.model.InvitationStatus;
import org.example.healthcare.model.Medic;
import org.example.healthcare.model.MedicInvitation;
import org.example.healthcare.repository.ClinicRepository;
import org.example.healthcare.repository.MedicInvitationRepository;
import org.example.healthcare.repository.MedicRepository;
import org.example.healthcare.service.MedicInvitationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MedicInvitationServiceImpl implements MedicInvitationService {

    private final MedicInvitationRepository invitationRepository;
    private final ClinicRepository          clinicRepository;
    private final MedicRepository           medicRepository;

    @Override
    @Transactional
    public MedicInvitationResponse invite(UUID clinicId, MedicInvitationRequest request) {
        Clinic clinic = clinicRepository.findById(clinicId)
                .orElseThrow(() -> new ResourceNotFoundException("Clinic", clinicId));

        if (invitationRepository.existsByEmailAndClinicIdAndStatus(
                request.email(), clinicId, InvitationStatus.PENDING))
            throw new BusinessException("A pending invitation already exists for " + request.email());

        String token = generateToken();
        MedicInvitation invitation = MedicInvitation.builder()
                .clinic(clinic)
                .email(request.email())
                .token(token)
                .build();

        // TODO: send invitation email with token link via EmailService
        return toResponse(invitationRepository.save(invitation));
    }

    @Override
    public List<MedicInvitationResponse> listByClinic(UUID clinicId) {
        return invitationRepository.findByClinicIdAndStatus(clinicId, InvitationStatus.PENDING)
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public MedicInvitationResponse accept(String token, UUID medicId) {
        MedicInvitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new BusinessException("Invalid invitation token"));

        if (!invitation.isValid())
            throw new BusinessException("Invitation token has expired or already been used");

        Medic medic = medicRepository.findById(medicId)
                .orElseThrow(() -> new ResourceNotFoundException("Medic", medicId));

        medic.setClinic(invitation.getClinic());
        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setAcceptedAt(Instant.now());

        return toResponse(invitation);
    }

    @Override
    @Transactional
    public void revoke(UUID invitationId, UUID requestingClinicId) {
        MedicInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation", invitationId));

        if (!invitation.getClinic().getId().equals(requestingClinicId))
            throw new BusinessException("Not your invitation");

        if (invitation.getStatus() != InvitationStatus.PENDING)
            throw new BusinessException("Only PENDING invitations can be revoked");

        invitationRepository.delete(invitation);
    }

    @Override
    @Transactional
    public int expireStale() {
        // Expire all PENDING invitations older than 72 hours
        return invitationRepository.expireBefore(Instant.now().minusSeconds(72 * 3600));
    }

    private String generateToken() {
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private MedicInvitationResponse toResponse(MedicInvitation i) {
        return new MedicInvitationResponse(
                i.getId(), i.getClinic().getId(), i.getEmail(),
                i.getStatus(), i.getCreatedAt(), i.getAcceptedAt());
    }
}
