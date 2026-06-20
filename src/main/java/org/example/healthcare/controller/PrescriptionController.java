package org.example.healthcare.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.healthcare.dto.prescription.PrescriptionRequest;
import org.example.healthcare.dto.prescription.PrescriptionResponse;
import org.example.healthcare.security.AppUserDetails;
import org.example.healthcare.service.PrescriptionService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Prescription endpoints.
 *
 * <p>Role guards:
 * POST → MEDIC (own consultation)
 * GET  → AUTH (patient or medic of the booking)
 */
@RestController
@RequestMapping("/api/v1/prescriptions")
@RequiredArgsConstructor
public class PrescriptionController {

    private final PrescriptionService prescriptionService;

    /** MEDIC — create a prescription after consultation completes */
    @PostMapping("/consultation/{consultationId}")
    @ResponseStatus(HttpStatus.CREATED)
    public PrescriptionResponse create(@PathVariable UUID consultationId,
                                       @AuthenticationPrincipal AppUserDetails principal,
                                       @Valid @RequestBody PrescriptionRequest request) {
        return prescriptionService.create(consultationId, principal.getUserId(), request);
    }

    /** AUTH — retrieve decrypted prescriptions for a consultation; empty list if none issued yet */
    @GetMapping("/consultation/{consultationId}")
    public List<PrescriptionResponse> getByConsultationId(@PathVariable UUID consultationId,
                                                          @AuthenticationPrincipal AppUserDetails principal) {
        return prescriptionService.getByConsultationId(consultationId, principal.getUserId());
    }

    /** AUTH (patient) — retrieve all of the authenticated patient's decrypted prescriptions, newest first */
    @GetMapping("/my")
    public List<PrescriptionResponse> getMyPrescriptions(@AuthenticationPrincipal AppUserDetails principal) {
        return prescriptionService.getForPatient(principal.getUserId());
    }
}
