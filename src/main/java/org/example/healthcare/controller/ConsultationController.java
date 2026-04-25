package org.example.healthcare.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.healthcare.dto.consultation.ConsultationNotesRequest;
import org.example.healthcare.dto.consultation.ConsultationResponse;
import org.example.healthcare.security.AppUserDetails;
import org.example.healthcare.service.ConsultationService;
import org.example.healthcare.service.MedicService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Consultation lifecycle management.
 *
 * <p>Role guards:
 * POST  start / complete / fail / notes → MEDIC (own booking)
 * GET   /{id}                           → AUTH (own booking party)
 * GET   history                         → PATIENT / MEDIC (own records)
 */
@RestController
@RequestMapping("/api/v1/consultations")
@RequiredArgsConstructor
public class ConsultationController {

    private final ConsultationService consultationService;
    private final MedicService medicService;

    /** MEDIC — open video room; transitions booking → IN_PROGRESS */
    @PostMapping("/start")
    @ResponseStatus(HttpStatus.CREATED)
    public ConsultationResponse start(@RequestParam UUID bookingId,
                                      @AuthenticationPrincipal AppUserDetails principal) {
        return consultationService.start(bookingId, principal.getUserId());
    }

    /** MEDIC — close room; records duration; sets release_at */
    @PostMapping("/{id}/complete")
    public ConsultationResponse complete(@PathVariable UUID id,
                                         @AuthenticationPrincipal AppUserDetails principal,
                                         @RequestParam int durationSeconds) {
        return consultationService.complete(id, principal.getUserId(), durationSeconds);
    }

    /** ADMIN / system — mark no-show or technical failure */
    @PostMapping("/{id}/fail")
    public ConsultationResponse markFailed(@PathVariable UUID id) {
        return consultationService.markFailed(id);
    }

    /** AUTH — get consultation details; decrypted notes returned only to medic */
    @GetMapping("/{id}")
    public ConsultationResponse getById(@PathVariable UUID id,
                                        @AuthenticationPrincipal AppUserDetails principal) {
        return consultationService.getById(id, principal.getUserId());
    }

    /** PATIENT — paginated consultation history */
    @GetMapping("/my/patient")
    public Page<ConsultationResponse> getPatientHistory(@AuthenticationPrincipal AppUserDetails principal,
                                                        Pageable pageable) {
        return consultationService.getPatientHistory(principal.getUserId(), pageable);
    }

    /** MEDIC — paginated consultation history by medic entity ID */
    @GetMapping("/my/medic")
    public Page<ConsultationResponse> getMedicHistory(@AuthenticationPrincipal AppUserDetails principal,
                                                      Pageable pageable) {
        UUID medicId = medicService.getByUserId(principal.getUserId()).id();
        return consultationService.getMedicHistory(medicId, pageable);
    }

    /** MEDIC — save / update encrypted consultation notes */
    @PutMapping("/{id}/notes")
    public ConsultationResponse saveNotes(@PathVariable UUID id,
                                          @AuthenticationPrincipal AppUserDetails principal,
                                          @Valid @RequestBody ConsultationNotesRequest request) {
        return consultationService.saveNotes(id, principal.getUserId(), request);
    }
}
