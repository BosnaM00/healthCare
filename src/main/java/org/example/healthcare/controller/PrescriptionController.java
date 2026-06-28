package org.example.healthcare.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.healthcare.dto.diagnosis.DiagnosisInferenceRequest;
import org.example.healthcare.dto.diagnosis.DiagnosisInferenceResponse;
import org.example.healthcare.dto.prescription.PrescriptionRequest;
import org.example.healthcare.dto.prescription.PrescriptionResponse;
import org.example.healthcare.security.AppUserDetails;
import org.example.healthcare.service.AiDiagnosisService;
import org.example.healthcare.service.PrescriptionService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final AiDiagnosisService aiDiagnosisService;

    /**
     * MEDIC — infer a likely diagnosis from a set of medications (AI decision-support).
     *
     * <p>Stateless: medications are supplied in the request body, nothing is read
     * from or written to storage. The result is a non-binding suggestion.
     */
    @PostMapping("/infer-diagnosis")
    @PreAuthorize("hasRole('MEDIC')")
    public DiagnosisInferenceResponse inferDiagnosis(@Valid @RequestBody DiagnosisInferenceRequest request) {
        return aiDiagnosisService.infer(request);
    }

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

    /** AUTH (patient or treating medic) — download the prescription as a generated PDF */
    @GetMapping("/{prescriptionId}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable UUID prescriptionId,
                                              @AuthenticationPrincipal AppUserDetails principal) {
        byte[] pdf = prescriptionService.generatePdf(prescriptionId, principal.getUserId());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"prescription-" + prescriptionId + ".pdf\"")
                .body(pdf);
    }
}
