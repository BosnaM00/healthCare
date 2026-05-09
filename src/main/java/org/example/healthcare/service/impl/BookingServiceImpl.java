package org.example.healthcare.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.healthcare.common.exception.BusinessException;
import org.example.healthcare.common.exception.ResourceNotFoundException;
import org.example.healthcare.dto.booking.BookingRequest;
import org.example.healthcare.dto.booking.BookingResponse;
import org.example.healthcare.dto.slot.SlotResponse;
import org.example.healthcare.event.BookingConfirmedEvent;
import org.example.healthcare.model.*;
import org.example.healthcare.repository.BookingRepository;
import org.example.healthcare.repository.MedicRepository;
import org.example.healthcare.repository.PaymentRepository;
import org.example.healthcare.repository.SlotRepository;
import org.example.healthcare.repository.UserRepository;
import org.example.healthcare.service.BookingService;
import org.example.healthcare.service.PaymentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
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
public class BookingServiceImpl implements BookingService {

    private final BookingRepository        bookingRepository;
    private final SlotRepository           slotRepository;
    private final MedicRepository          medicRepository;
    private final UserRepository           userRepository;
    private final PaymentService           paymentService;
    private final PaymentRepository        paymentRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** Default consultation fee in major currency units (RON) — override per medic in a future phase */
    @Value("${app.payment.default-consultation-fee:150.00}")
    private java.math.BigDecimal defaultConsultationFee;

    @Override
    @Transactional
    public BookingResponse create(UUID patientId, BookingRequest request) {
        // Pessimistic lock prevents double-booking on concurrent requests
        Slot slot = slotRepository.findByIdWithLock(request.slotId())
                .orElseThrow(() -> new ResourceNotFoundException("Slot", request.slotId()));

        if (slot.getStatus() != SlotStatus.AVAILABLE)
            throw new BusinessException("Slot is not available");

        Medic medic = medicRepository.findById(request.medicId())
                .orElseThrow(() -> new ResourceNotFoundException("Medic", request.medicId()));
        User patient = userRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("User", patientId));

        if (!slot.getMedic().getId().equals(medic.getId()))
            throw new BusinessException("Slot does not belong to this medic");

        Booking booking = Booking.builder()
                .patient(patient)
                .medic(medic)
                .slot(slot)
                .consultationType(request.consultationType())
                .paymentStatus(BookingPaymentStatus.PENDING)
                .cancellationPolicyAcceptedAt(
                        request.cancellationPolicyAccepted() ? Instant.now() : null)
                .build();
        bookingRepository.save(booking);

        // Create Stripe PaymentIntent (escrow) and persist the Payment record
        String stripeCustomerId = patient.getStripeCustomerId();
        paymentService.reserve(booking.getId(), defaultConsultationFee, stripeCustomerId);

        slot.setStatus(SlotStatus.BOOKED);

        // Publish event so ConsultationServiceImpl can create the Consultation and provision the video room.
        // Fired after the booking transaction commits so the event listener reads a consistent DB state.
        eventPublisher.publishEvent(new BookingConfirmedEvent(
                this, booking.getId(), patient.getId(), medic.getUser().getId()));

        return toResponse(booking);
    }

    @Override
    public BookingResponse getById(UUID id, UUID principalId) {
        Booking booking = findOrThrow(id);
        boolean isPatient = booking.getPatient().getId().equals(principalId);
        boolean isMedic   = booking.getMedic().getUser().getId().equals(principalId);
        if (!isPatient && !isMedic)
            throw new BusinessException("Access denied");
        return toResponse(booking);
    }

    @Override
    public Page<BookingResponse> getPatientBookings(UUID patientId, Pageable pageable) {
        return bookingRepository.findByPatientId(patientId, pageable).map(this::toResponse);
    }

    @Override
    public Page<BookingResponse> getMedicBookings(UUID medicId, Pageable pageable) {
        return bookingRepository.findByMedicId(medicId, pageable).map(this::toResponse);
    }

    @Override
    @Transactional
    public void cancel(UUID id, UUID patientId) {
        Booking booking = findOrThrow(id);
        if (!booking.getPatient().getId().equals(patientId))
            throw new BusinessException("Not your booking");
        if (booking.getPaymentStatus() == BookingPaymentStatus.REFUNDED)
            throw new BusinessException("Already cancelled");

        // Business rule: full refund if cancelled > 24h before slot start
        boolean fullRefund = Instant.now()
                .isBefore(booking.getSlot().getStartsAt().minus(24, ChronoUnit.HOURS));

        paymentRepository.findByBookingId(id).ifPresent(payment ->
                paymentService.refund(payment.getId(), fullRefund ? null : payment.getPlatformFee()));

        booking.setPaymentStatus(BookingPaymentStatus.REFUNDED);
        booking.getSlot().setStatus(SlotStatus.AVAILABLE);
    }

    private Booking findOrThrow(UUID id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", id));
    }

    private SlotResponse toSlotResponse(Slot slot) {
        return new SlotResponse(
                slot.getId(),
                slot.getMedic().getId(),
                slot.getStartsAt(),   // maps to JSON "startTime"
                slot.getEndsAt(),     // maps to JSON "endTime"
                slot.getStatus()
        );
    }

    private BookingResponse toResponse(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getPatient().getId(),
                booking.getMedic().getId(),
                booking.getSlot().getId(),
                booking.getConsultationType(),
                booking.getPaymentStatus(),
                booking.getCancellationPolicyAcceptedAt(),
                booking.getCreatedAt(),
                toSlotResponse(booking.getSlot())
        );
    }
}
