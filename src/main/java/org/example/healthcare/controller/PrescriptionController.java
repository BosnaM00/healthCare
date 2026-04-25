package org.example.healthcare.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.healthcare.dto.prescription.PrescriptionRequest;
import org.example.healthcare.dto.prescription.PrescriptionResponse;
import org.example.healthcare.service.PrescriptionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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
                                       @RequestParam UUID medicUserId,
                                       @Valid @RequestBody PrescriptionRequest request) {
        return prescriptionService.create(consultationId, medicUserId, request);
    }

    /** AUTH — retrieve decrypted prescription */
    @GetMapping("/consultation/{consultationId}")
    public PrescriptionResponse getByConsultationId(@PathVariable UUID consultationId,
                                                    @RequestParam UUID principalId) {
        return prescriptionService.getByConsultationId(consultationId, principalId);
    }
}
