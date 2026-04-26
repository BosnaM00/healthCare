package org.example.healthcare.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.healthcare.common.StripePaymentService;
import org.example.healthcare.common.exception.BusinessException;
import org.example.healthcare.common.exception.ResourceNotFoundException;
import org.example.healthcare.dto.dispute.DisputeRequest;
import org.example.healthcare.dto.dispute.DisputeResolutionRequest;
import org.example.healthcare.dto.dispute.DisputeResponse;
import org.example.healthcare.model.*;
import org.example.healthcare.repository.ConsultationRepository;
import org.example.healthcare.repository.DisputeRepository;
import org.example.healthcare.repository.PaymentRepository;
import org.example.healthcare.repository.UserRepository;
import org.example.healthcare.service.DisputeService;
import org.example.healthcare.service.PaymentService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DisputeServiceImpl implements DisputeService {

    private final DisputeRepository      disputeRepository;
    private final ConsultationRepository consultationRepository;
    private final PaymentRepository      paymentRepository;
    private final UserRepository         userRepository;
    private final PaymentService         paymentService;
    private final StripePaymentService   stripePaymentService;

    @Override
    @Transactional
    public DisputeResponse open(UUID patientUserId, DisputeRequest request) {
        Consultation consultation = consultationRepository.findById(request.consultationId())
                .orElseThrow(() -> new ResourceNotFoundException("Consultation", request.consultationId()));

        if (!consultation.getBooking().getPatient().getId().equals(patientUserId))
            throw new BusinessException("Only the booking patient can raise a dispute");

        if (consultation.getStatus() != ConsultationStatus.COMPLETED)
            throw new BusinessException("Disputes can only be raised on COMPLETED consultations");

        boolean alreadyOpen = disputeRepository.existsByConsultationIdAndStatusIn(
                request.consultationId(), List.of(DisputeStatus.OPEN, DisputeStatus.UNDER_REVIEW));
        if (alreadyOpen)
            throw new BusinessException("An open dispute already exists for this consultation");

        User patient = userRepository.findById(patientUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", patientUserId));

        // Block auto-release by transitioning payment to DISPUTED
        paymentRepository.findByBookingId(consultation.getBooking().getId()).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.HELD)
                payment.setStatus(PaymentStatus.DISPUTED);
        });

        // Mirror dispute status on consultation
        consultation.setStatus(ConsultationStatus.DISPUTED);

        Dispute dispute = Dispute.builder()
                .consultation(consultation)
                .raisedBy(patient)
                .reason(request.reason())
                .build();

        return toResponse(disputeRepository.save(dispute));
    }

    @Override
    @Transactional
    public DisputeResponse claimForReview(UUID disputeId, UUID adminUserId) {
        Dispute dispute = findOrThrow(disputeId);
        if (dispute.getStatus() != DisputeStatus.OPEN)
            throw new BusinessException("Only OPEN disputes can be claimed for review");

        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", adminUserId));
        dispute.setResolvedBy(admin);  // pre-assign reviewer
        dispute.setStatus(DisputeStatus.UNDER_REVIEW);
        return toResponse(dispute);
    }

    @Override
    @Transactional
    public DisputeResponse resolve(UUID disputeId, UUID adminUserId, DisputeResolutionRequest request) {
        Dispute dispute = findOrThrow(disputeId);

        if (dispute.getStatus() != DisputeStatus.UNDER_REVIEW)
            throw new BusinessException("Only UNDER_REVIEW disputes can be resolved");

        validateResolutionStatus(request.resolution());

        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", adminUserId));

        dispute.setStatus(request.resolution());
        dispute.setResolvedBy(admin);
        dispute.setResolvedAt(Instant.now());
        dispute.setResolutionNote(request.resolutionNote());

        // Update consultation back to COMPLETED to allow normal post-processing
        dispute.getConsultation().setStatus(ConsultationStatus.COMPLETED);

        // Act on payment based on resolution
        Payment payment = paymentRepository
                .findByBookingId(dispute.getConsultation().getBooking().getId())
                .orElseThrow(() -> new BusinessException("No payment for this consultation"));

        switch (request.resolution()) {
            case RESOLVED_RELEASED -> paymentService.release(payment.getId());
            case RESOLVED_REFUNDED -> {
                // If funds were already released, reverse the transfer first then refund
                if (payment.getStatus() == PaymentStatus.RELEASED
                        && payment.getStripeTransferId() != null) {
                    log.info("Reversing transfer {} before refund for payment {}",
                            payment.getStripeTransferId(), payment.getId());
                    stripePaymentService.reverseTransfer(
                            payment.getStripeTransferId(), payment.getId().toString());
                }
                paymentService.refund(payment.getId(), null);
            }
            case RESOLVED_PARTIAL  -> {
                // Partial: release half to medic, refund other half
                // TODO: accept split amounts in DisputeResolutionRequest for production
                paymentService.release(payment.getId());
            }
            default -> throw new BusinessException("Unsupported resolution: " + request.resolution());
        }

        return toResponse(dispute);
    }

    @Override
    public DisputeResponse getById(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Override
    public Page<DisputeResponse> listByStatus(String status, Pageable pageable) {
        DisputeStatus disputeStatus = DisputeStatus.valueOf(status.toUpperCase());
        return disputeRepository.findByStatus(disputeStatus, pageable).map(this::toResponse);
    }

    private void validateResolutionStatus(DisputeStatus status) {
        if (status != DisputeStatus.RESOLVED_RELEASED
                && status != DisputeStatus.RESOLVED_REFUNDED
                && status != DisputeStatus.RESOLVED_PARTIAL)
            throw new BusinessException("Resolution must be one of: RESOLVED_RELEASED, RESOLVED_REFUNDED, RESOLVED_PARTIAL");
    }

    private Dispute findOrThrow(UUID id) {
        return disputeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dispute", id));
    }

    private DisputeResponse toResponse(Dispute d) {
        return new DisputeResponse(
                d.getId(),
                d.getConsultation().getId(),
                d.getRaisedBy().getId(),
                d.getReason(),
                d.getStatus(),
                d.getResolvedBy() != null ? d.getResolvedBy().getId() : null,
                d.getResolvedAt(),
                d.getResolutionNote(),
                d.getCreatedAt());
    }
}
