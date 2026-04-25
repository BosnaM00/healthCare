package org.example.healthcare.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.healthcare.common.StripePaymentService;
import org.example.healthcare.common.exception.BusinessException;
import org.example.healthcare.common.exception.ResourceNotFoundException;
import org.example.healthcare.dto.payment.PaymentResponse;
import org.example.healthcare.model.*;
import org.example.healthcare.repository.BookingRepository;
import org.example.healthcare.repository.PaymentRepository;
import org.example.healthcare.service.PaymentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentServiceImpl implements PaymentService {

    /**
     * Default commission rate for independent medics (no clinic).
     * Clinic-employed medics use the clinic's own commission_rate.
     */
    @Value("${app.payment.independent-commission-rate:0.15}")
    private BigDecimal independentCommissionRate;

    @Value("${app.payment.currency:ron}")
    private String currency;

    private final PaymentRepository    paymentRepository;
    private final BookingRepository    bookingRepository;
    private final StripePaymentService stripePaymentService;

    @Override
    @Transactional
    public PaymentResponse reserve(UUID bookingId, BigDecimal amount, String stripeCustomerId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));

        if (paymentRepository.findByBookingId(bookingId).isPresent())
            throw new BusinessException("Payment already exists for booking " + bookingId);

        BigDecimal commissionRate = resolveCommissionRate(booking.getMedic());
        BigDecimal platformFee   = amount.multiply(commissionRate).setScale(2, RoundingMode.HALF_UP);

        String piId = stripePaymentService.createPaymentIntent(amount, currency, stripeCustomerId);

        Payment payment = Payment.builder()
                .booking(booking)
                .stripePaymentIntentId(piId)
                .amount(amount)
                .platformFee(platformFee)
                .status(PaymentStatus.RESERVED)
                .build();

        // Mark booking payment status as PAID (intent authorised)
        booking.setPaymentStatus(BookingPaymentStatus.PAID);

        return toResponse(paymentRepository.save(payment));
    }

    @Override
    public PaymentResponse getByBookingId(UUID bookingId, UUID principalId) {
        Payment payment = paymentRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment for Booking", bookingId));
        boolean isPatient = payment.getBooking().getPatient().getId().equals(principalId);
        boolean isMedic   = payment.getBooking().getMedic().getUser().getId().equals(principalId);
        if (!isPatient && !isMedic)
            throw new BusinessException("Access denied");
        return toResponse(payment);
    }

    @Override
    public Page<PaymentResponse> getPatientPayments(UUID patientId, Pageable pageable) {
        return paymentRepository.findByPatientId(patientId, pageable).map(this::toResponse);
    }

    @Override
    public Page<PaymentResponse> getMedicPayments(UUID medicId, Pageable pageable) {
        return paymentRepository.findByMedicId(medicId, pageable).map(this::toResponse);
    }

    @Override
    @Transactional
    public void release(UUID paymentId) {
        Payment payment = findOrThrow(paymentId);

        if (payment.getStatus() != PaymentStatus.HELD)
            throw new BusinessException("Only HELD payments can be released; current status: " + payment.getStatus());

        Medic medic       = payment.getBooking().getMedic();
        String destAccount = resolveStripeAccount(medic);
        BigDecimal netAmount = payment.getAmount().subtract(payment.getPlatformFee());

        String transferId = stripePaymentService.transferToAccount(
                payment.getStripePaymentIntentId(), destAccount, netAmount);

        payment.setStripeTransferId(transferId);
        payment.setStatus(PaymentStatus.RELEASED);
        payment.setReleasedAt(Instant.now());
    }

    @Override
    @Transactional
    public void refund(UUID paymentId, BigDecimal amount) {
        Payment payment = findOrThrow(paymentId);

        if (payment.getStatus() == PaymentStatus.RELEASED)
            throw new BusinessException("Cannot refund an already released payment");
        if (payment.getStatus() == PaymentStatus.REFUNDED)
            throw new BusinessException("Payment already refunded");

        stripePaymentService.refund(payment.getStripePaymentIntentId(), amount);
        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setRefundedAt(Instant.now());

        // Sync booking payment status
        payment.getBooking().setPaymentStatus(BookingPaymentStatus.REFUNDED);
    }

    // --- helpers ---

    private BigDecimal resolveCommissionRate(Medic medic) {
        if (medic.getClinic() != null) return medic.getClinic().getCommissionRate();
        return independentCommissionRate;
    }

    private String resolveStripeAccount(Medic medic) {
        if (medic.getClinic() != null && medic.getClinic().getStripeAccountId() != null)
            return medic.getClinic().getStripeAccountId();
        if (medic.getStripeAccountId() != null)
            return medic.getStripeAccountId();
        throw new BusinessException("No Stripe account configured for medic " + medic.getId());
    }

    private Payment findOrThrow(UUID id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", id));
    }

    private PaymentResponse toResponse(Payment p) {
        return new PaymentResponse(
                p.getId(), p.getBooking().getId(), p.getStripePaymentIntentId(),
                p.getAmount(), p.getPlatformFee(), p.getStatus(),
                p.getReleasedAt(), p.getRefundedAt());
    }
}
