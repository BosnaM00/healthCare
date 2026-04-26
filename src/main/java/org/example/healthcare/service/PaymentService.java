package org.example.healthcare.service;

import org.example.healthcare.dto.payment.PaymentResponse;
import org.example.healthcare.model.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentService {

    /**
     * Creates the Payment record in RESERVED state at booking time.
     * Calls StripePaymentService to create the PaymentIntent (manual-capture escrow).
     *
     * @param bookingId        newly created booking
     * @param amount           total charge in major units
     * @param stripeCustomerId patient's Stripe customer ID
     * @return persisted Payment
     */
    PaymentResponse reserve(UUID bookingId, BigDecimal amount, String stripeCustomerId);

    PaymentResponse getByBookingId(UUID bookingId, UUID principalId);

    /** Returns the raw {@link Payment} entity by id, or null if not found. Used by controllers to access transient fields like clientSecret. */
    Payment findById(UUID paymentId);

    Page<PaymentResponse> getPatientPayments(UUID patientId, Pageable pageable);

    Page<PaymentResponse> getMedicPayments(UUID medicId, Pageable pageable);

    /**
     * Releases a HELD payment to the medic/clinic Stripe account.
     * Called by PaymentReleaseJob — should not be exposed via a public API endpoint.
     */
    void release(UUID paymentId);

    /**
     * Issues a full refund. Transitions RESERVED/HELD → REFUNDED.
     * Called by BookingService.cancel() and DisputeService on RESOLVED_REFUNDED.
     */
    void refund(UUID paymentId, BigDecimal amount);
}
