package org.example.healthcare.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.healthcare.common.EncryptionService;
import org.example.healthcare.common.StripePaymentService;
import org.example.healthcare.common.exception.BusinessException;
import org.example.healthcare.common.exception.ResourceNotFoundException;
import org.example.healthcare.dto.consultation.ConsultationNotesRequest;
import org.example.healthcare.dto.consultation.ConsultationResponse;
import org.example.healthcare.model.*;
import org.example.healthcare.repository.BookingRepository;
import org.example.healthcare.repository.ConsultationRepository;
import org.example.healthcare.repository.PaymentRepository;
import org.example.healthcare.service.ConsultationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsultationServiceImpl implements ConsultationService {

    /** Dispute window: payment is released 48 h after consultation ends */
    private static final long RELEASE_WINDOW_HOURS = 48;

    private final ConsultationRepository consultationRepository;
    private final BookingRepository      bookingRepository;
    private final PaymentRepository      paymentRepository;
    private final EncryptionService      encryptionService;
    private final StripePaymentService   stripePaymentService;

    @Override
    @Transactional
    public ConsultationResponse start(UUID bookingId, UUID medicUserId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));

        if (!booking.getMedic().getUser().getId().equals(medicUserId))
            throw new BusinessException("Only the booked medic can start this consultation");

        if (consultationRepository.findByBookingId(bookingId).isPresent())
            throw new BusinessException("Consultation already started for this booking");

        Consultation consultation = Consultation.builder()
                .booking(booking)
                .status(ConsultationStatus.IN_PROGRESS)
                .videoRoomId("room_" + bookingId)   // TODO: call video provider SDK
                .startedAt(Instant.now())
                .build();
        consultation = consultationRepository.save(consultation);

        // Capture the Stripe escrow now that the session is live
        Payment payment = paymentRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new BusinessException("No payment record for booking " + bookingId));
        stripePaymentService.capturePaymentIntent(payment.getStripePaymentIntentId());
        payment.setStatus(PaymentStatus.HELD);

        return toResponse(consultation, null);
    }

    @Override
    @Transactional
    public ConsultationResponse complete(UUID consultationId, UUID medicUserId, int durationSeconds) {
        Consultation consultation = findOrThrow(consultationId);
        assertMedicOwns(consultation, medicUserId);

        if (consultation.getStatus() != ConsultationStatus.IN_PROGRESS)
            throw new BusinessException("Consultation is not IN_PROGRESS");

        Instant now = Instant.now();
        consultation.setStatus(ConsultationStatus.COMPLETED);
        consultation.setEndedAt(now);
        consultation.setDurationSeconds(durationSeconds);
        consultation.setReleaseAt(now.plus(RELEASE_WINDOW_HOURS, ChronoUnit.HOURS));

        return toResponse(consultation, null);
    }

    @Override
    @Transactional
    public ConsultationResponse markFailed(UUID consultationId) {
        Consultation consultation = findOrThrow(consultationId);

        if (consultation.getStatus() == ConsultationStatus.COMPLETED)
            throw new BusinessException("Cannot fail a completed consultation");

        consultation.setStatus(ConsultationStatus.FAILED);
        consultation.setEndedAt(Instant.now());

        // Trigger automatic refund for failed sessions
        paymentRepository.findByBookingId(consultation.getBooking().getId()).ifPresent(payment -> {
            stripePaymentService.refund(payment.getStripePaymentIntentId(), null);
            payment.setStatus(PaymentStatus.REFUNDED);
            payment.setRefundedAt(Instant.now());
        });

        return toResponse(consultation, null);
    }

    @Override
    public ConsultationResponse getById(UUID id, UUID principalId) {
        Consultation consultation = findOrThrow(id);
        boolean isPatient = consultation.getBooking().getPatient().getId().equals(principalId);
        boolean isMedic   = consultation.getBooking().getMedic().getUser().getId().equals(principalId);
        if (!isPatient && !isMedic)
            throw new BusinessException("Access denied");

        String notes = null;
        if (isMedic && consultation.getNotesEncrypted() != null)
            notes = encryptionService.decrypt(consultation.getNotesEncrypted());

        return toResponse(consultation, notes);
    }

    @Override
    public Page<ConsultationResponse> getPatientHistory(UUID patientId, Pageable pageable) {
        return consultationRepository.findByPatientId(patientId, pageable)
                .map(c -> toResponse(c, null));
    }

    @Override
    public Page<ConsultationResponse> getMedicHistory(UUID medicId, Pageable pageable) {
        return consultationRepository.findByMedicId(medicId, pageable)
                .map(c -> toResponse(c, null));
    }

    @Override
    @Transactional
    public ConsultationResponse saveNotes(UUID consultationId, UUID medicUserId, ConsultationNotesRequest request) {
        Consultation consultation = findOrThrow(consultationId);
        assertMedicOwns(consultation, medicUserId);

        if (consultation.getStatus() != ConsultationStatus.COMPLETED)
            throw new BusinessException("Notes can only be saved after consultation is COMPLETED");

        consultation.setNotesEncrypted(encryptionService.encrypt(request.notes()));
        return toResponse(consultation, request.notes());
    }

    private void assertMedicOwns(Consultation consultation, UUID medicUserId) {
        if (!consultation.getBooking().getMedic().getUser().getId().equals(medicUserId))
            throw new BusinessException("Access denied");
    }

    private Consultation findOrThrow(UUID id) {
        return consultationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation", id));
    }

    private ConsultationResponse toResponse(Consultation c, String decryptedNotes) {
        return new ConsultationResponse(
                c.getId(),
                c.getBooking().getId(),
                c.getStatus(),
                c.getVideoRoomId(),
                c.getStartedAt(),
                c.getEndedAt(),
                c.getDurationSeconds(),
                decryptedNotes,
                c.getReleaseAt());
    }
}
