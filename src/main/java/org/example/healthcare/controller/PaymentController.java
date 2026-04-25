package org.example.healthcare.controller;

import lombok.RequiredArgsConstructor;
import org.example.healthcare.dto.payment.PaymentResponse;
import org.example.healthcare.security.AppUserDetails;
import org.example.healthcare.service.MedicService;
import org.example.healthcare.service.PaymentService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Payment read-only query endpoints.
 *
 * <p>Write operations (reserve, release, refund) are triggered internally by
 * BookingService, ConsultationService, DisputeService, and PaymentReleaseJob —
 * they are NOT exposed via API to prevent direct payment state manipulation.
 *
 * <p>Role guards:
 * GET by booking → AUTH (own booking party)
 * GET patient    → PATIENT (own payments)
 * GET medic      → MEDIC (own payments)
 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final MedicService medicService;

    @GetMapping("/booking/{bookingId}")
    public PaymentResponse getByBookingId(@PathVariable UUID bookingId,
                                          @AuthenticationPrincipal AppUserDetails principal) {
        return paymentService.getByBookingId(bookingId, principal.getUserId());
    }

    @GetMapping("/my/patient")
    public Page<PaymentResponse> getPatientPayments(@AuthenticationPrincipal AppUserDetails principal,
                                                    Pageable pageable) {
        return paymentService.getPatientPayments(principal.getUserId(), pageable);
    }

    @GetMapping("/my/medic")
    public Page<PaymentResponse> getMedicPayments(@AuthenticationPrincipal AppUserDetails principal,
                                                  Pageable pageable) {
        UUID medicId = medicService.getByUserId(principal.getUserId()).id();
        return paymentService.getMedicPayments(medicId, pageable);
    }
}
