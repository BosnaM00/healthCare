package org.example.healthcare.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.healthcare.common.StripePaymentService;
import org.example.healthcare.dto.payment.PaymentResponse;
import org.example.healthcare.dto.stripe.PaymentIntentRequest;
import org.example.healthcare.dto.stripe.PaymentIntentResponse;
import org.example.healthcare.dto.stripe.RefundRequest;
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

    private final PaymentService        paymentService;
    private final MedicService          medicService;
    private final StripePaymentService  stripePaymentService;

    @Value("${stripe.publishable-key:pk_test_placeholder}")
    private String publishableKey;

    /**
     * Returns the Stripe {@code client_secret} for the booking's payment so Stripe.js
     * can confirm it on the frontend.
     *
     * <p>The Payment (and its PaymentIntent) is created during booking creation, so this
     * endpoint does NOT create a new one — it looks up the existing payment (ownership
     * enforced by {@link PaymentService#getByBookingId}) and fetches the client_secret
     * fresh from Stripe.
     *
     * <p>Role: {@code PATIENT} only. The patient must own the booking.
     */
    @PostMapping("/intents")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PATIENT')")
    public PaymentIntentResponse createPaymentIntent(
            @Valid @RequestBody PaymentIntentRequest request,
            @AuthenticationPrincipal AppUserDetails principal) {

        // Ownership-checked lookup of the payment reserved at booking time.
        PaymentResponse payment = paymentService.getByBookingId(request.bookingId(), principal.getUserId());

        String clientSecret = stripePaymentService.retrieveClientSecret(payment.stripePaymentIntentId());

        return new PaymentIntentResponse(payment.id(), clientSecret, publishableKey);
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
