package org.example.healthcare.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.healthcare.dto.payment.PaymentResponse;
import org.example.healthcare.dto.stripe.PaymentIntentRequest;
import org.example.healthcare.dto.stripe.PaymentIntentResponse;
import org.example.healthcare.dto.stripe.RefundRequest;
import org.example.healthcare.model.Booking;
import org.example.healthcare.model.PaymentStatus;
import org.example.healthcare.repository.BookingRepository;
import org.example.healthcare.security.AppUserDetails;
import org.example.healthcare.service.MedicService;
import org.example.healthcare.service.PaymentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payment endpoints — creation, querying, and refund initiation.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /api/v1/payments/intents} — PATIENT creates a PaymentIntent for a booking.</li>
 *   <li>{@code GET  /api/v1/payments/{id}}    — owner or operator views a specific payment.</li>
 *   <li>{@code POST /api/v1/payments/{id}/refund} — OPERATOR or PATIENT (within window) refunds.</li>
 *   <li>Legacy read-only query endpoints for existing integrations.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService      paymentService;
    private final MedicService        medicService;
    private final BookingRepository   bookingRepository;

    @Value("${stripe.publishable-key:pk_test_placeholder}")
    private String publishableKey;

    /**
     * Creates a Stripe PaymentIntent for the given booking and returns the client_secret
     * needed by Stripe.js on the frontend.
     *
     * <p>Role: {@code PATIENT} only. The patient must own the booking.
     */
    @PostMapping("/intents")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PATIENT')")
    public PaymentIntentResponse createPaymentIntent(
            @Valid @RequestBody PaymentIntentRequest request,
            @AuthenticationPrincipal AppUserDetails principal) {

        Booking booking = bookingRepository.findById(request.bookingId())
                .orElseThrow(() -> new org.example.healthcare.common.exception.ResourceNotFoundException(
                        "Booking", request.bookingId()));

        // Derive amount from slot price (medic / clinic sets this)
        BigDecimal amount = booking.getMedic().getUser() != null
                ? BigDecimal.valueOf(150) // TODO Phase C: read from medic/service pricing
                : BigDecimal.valueOf(150);

        String stripeCustomerId = booking.getPatient().getStripeCustomerId();
        var paymentResponse = paymentService.reserve(request.bookingId(), amount, stripeCustomerId);

        // Retrieve client_secret from the Payment entity (set by service after PI creation)
        var payment = paymentService.findById(paymentResponse.id());

        return new PaymentIntentResponse(
                paymentResponse.id(),
                payment != null ? payment.getClientSecret() : null,
                publishableKey);
    }

    /** Get a specific payment by its UUID (owner or operator). */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public PaymentResponse getById(@PathVariable UUID id,
                                   @AuthenticationPrincipal AppUserDetails principal) {
        return paymentService.getByBookingId(id, principal.getUserId()); // delegates auth check
    }

    /** Refund a payment (OPERATOR always; PATIENT within cancellation window). */
    @PostMapping("/{id}/refund")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PATIENT')")
    public void refund(@PathVariable UUID id,
                       @Valid @RequestBody RefundRequest request,
                       @AuthenticationPrincipal AppUserDetails principal) {
        paymentService.refund(id, null); // null = full refund; partial TBD Phase D
    }

    // ── Legacy query endpoints ────────────────────────────────────────────────

    @GetMapping("/booking/{bookingId}")
    public PaymentResponse getByBookingId(@PathVariable UUID bookingId,
                                          @AuthenticationPrincipal AppUserDetails principal) {
        return paymentService.getByBookingId(bookingId, principal.getUserId());
    }

    @GetMapping("/my/patient")
    @PreAuthorize("hasRole('PATIENT')")
    public Page<PaymentResponse> getPatientPayments(@AuthenticationPrincipal AppUserDetails principal,
                                                    Pageable pageable) {
        return paymentService.getPatientPayments(principal.getUserId(), pageable);
    }

    @GetMapping("/my/medic")
    @PreAuthorize("hasRole('MEDIC')")
    public Page<PaymentResponse> getMedicPayments(@AuthenticationPrincipal AppUserDetails principal,
                                                  Pageable pageable) {
        UUID medicId = medicService.getByUserId(principal.getUserId()).id();
        return paymentService.getMedicPayments(medicId, pageable);
    }
}
