package org.example.healthcare.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.healthcare.common.RonAmount;
import org.example.healthcare.common.StripePaymentService;
import org.example.healthcare.common.exception.BusinessException;
import org.example.healthcare.common.exception.ResourceNotFoundException;
import org.example.healthcare.dto.payment.PaymentResponse;
import org.example.healthcare.model.*;
import org.example.healthcare.repository.BookingRepository;
import org.example.healthcare.repository.PaymentRepository;
import org.example.healthcare.service.AuditLogService;
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

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentServiceImpl implements PaymentService {

    @Value("${app.payment.independent-commission-rate:0.15}")
    private BigDecimal independentCommissionRate;

    @Value("${app.payment.currency:ron}")
    private String currency;

    @Value("${app.fee.independent-pct:15}")
    private int independentFeePct;

    @Value("${app.fee.clinic-pct:10}")
    private int clinicFeePct;

    private final PaymentRepository    paymentRepository;
    private final BookingRepository    bookingRepository;
    private final StripePaymentService stripePaymentService;
    private final AuditLogService      auditLogService;

    @Override
    @Transactional
    public PaymentResponse reserve(UUID bookingId, BigDecimal amount, String stripeCustomerId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));

        if (paymentRepository.findByBookingId(bookingId).isPresent())
            throw new BusinessException("Payment already exists for booking " + bookingId);

        int feePct          = resolveFeePct(booking.getMedic());
        RonAmount ronAmount = RonAmount.of(amount);
        long amountBani     = ronAmount.toBani();
        long feeBani        = ronAmount.computeFeeBani(feePct);
        BigDecimal platformFee = new BigDecimal(feeBani).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        String piId = stripePaymentService.createPaymentIntent(amount, currency, stripeCustomerId);

        Payment payment = Payment.builder()
                .booking(booking)
                .patientId(booking.getPatient().getId())
                .medicId(booking.getMedic().getId())
                .stripePaymentIntentId(piId)
                .amount(amount)
                .amountBani(amountBani)
                .platformFee(platformFee)
                .applicationFeeBani(feeBani)
                .currency("RON")
                .status(PaymentStatus.RESERVED)
                .build();

        // Mark booking payment status as PAID (intent authorised)
        booking.setPaymentStatus(BookingPaymentStatus.PAID);

        Payment saved = paymentRepository.save(payment);
        auditLogService.log(null, "PAYMENT_RESERVED", "Payment", saved.getId(),
                null, "status=RESERVED,bookingId=" + bookingId, null);

        return toResponse(saved);
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
        transitionState(payment, PaymentStatus.HELD, PaymentStatus.RELEASED);

        Medic medic        = payment.getBooking().getMedic();
        String destAccount = resolveStripeAccount(medic);
        BigDecimal netAmount = payment.getAmount().subtract(payment.getPlatformFee());

        String transferId = stripePaymentService.transferToAccount(
                payment.getStripePaymentIntentId(),
                destAccount,
                netAmount,
                payment.getBooking().getId().toString(),
                paymentId.toString(),
                payment.getStripeChargeId());

        payment.setStripeTransferId(transferId);
        payment.setReleasedAt(Instant.now());

        auditLogService.log(null, "PAYMENT_RELEASED", "Payment", paymentId,
                "status=HELD", "status=RELEASED,transferId=" + transferId, null);
        log.info("Payment {} released to Stripe account {} (transfer {})",
                paymentId, destAccount, transferId);
    }

    @Override
    @Transactional
    public void refund(UUID paymentId, BigDecimal amount) {
        Payment payment = findOrThrow(paymentId);

        if (payment.getStatus() == PaymentStatus.RELEASED)
            throw new BusinessException("Cannot refund an already released payment");
        if (payment.getStatus() == PaymentStatus.REFUNDED)
            throw new BusinessException("Payment already refunded");

        String fromState = payment.getStatus().name();
        stripePaymentService.refund(payment.getStripePaymentIntentId(), amount,
                paymentId.toString(), "requested_by_customer");
        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setRefundedAt(Instant.now());

        // Sync booking payment status
        payment.getBooking().setPaymentStatus(BookingPaymentStatus.REFUNDED);

        auditLogService.log(null, "PAYMENT_REFUNDED", "Payment", paymentId,
                "status=" + fromState, "status=REFUNDED", null);
    }

    // ── State machine guard ───────────────────────────────────────────────────

    /**
     * Transitions payment state, throwing if the current state is not the expected one.
     * The optimistic-lock {@code @Version} on {@link Payment} ensures concurrent callers
     * fail with {@link jakarta.persistence.OptimisticLockException} (surfaced as HTTP 409).
     */
    void transitionState(Payment payment, PaymentStatus expectedFrom, PaymentStatus to) {
        if (payment.getStatus() != expectedFrom) {
            throw new BusinessException(String.format(
                    "Expected payment %s to be in state %s but was %s",
                    payment.getId(), expectedFrom, payment.getStatus()));
        }
        payment.setStatus(to);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int resolveFeePct(Medic medic) {
        return medic.getClinic() != null ? clinicFeePct : independentFeePct;
    }

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
