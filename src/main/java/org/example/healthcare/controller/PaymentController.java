package org.example.healthcare.controller;

import lombok.RequiredArgsConstructor;
import org.example.healthcare.dto.payment.PaymentResponse;
import org.example.healthcare.service.PaymentService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @GetMapping("/booking/{bookingId}")
    public PaymentResponse getByBookingId(@PathVariable UUID bookingId,
                                          @RequestParam UUID principalId) {
        return paymentService.getByBookingId(bookingId, principalId);
    }

    @GetMapping("/my/patient")
    public Page<PaymentResponse> getPatientPayments(@RequestParam UUID patientId,
                                                    Pageable pageable) {
        return paymentService.getPatientPayments(patientId, pageable);
    }

    @GetMapping("/my/medic")
    public Page<PaymentResponse> getMedicPayments(@RequestParam UUID medicId,
                                                  Pageable pageable) {
        return paymentService.getMedicPayments(medicId, pageable);
    }
}
