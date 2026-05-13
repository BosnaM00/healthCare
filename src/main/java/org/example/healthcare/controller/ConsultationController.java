package org.example.healthcare.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.healthcare.dto.consultation.*;
import org.example.healthcare.security.AppUserDetails;
import org.example.healthcare.service.ConsultationService;
import org.example.healthcare.service.MedicService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Consultation lifecycle management.
 *
 * <p>Role guards:
 * <pre>
 * POST  /start                   → MEDIC (own booking)
 * POST  /{id}/complete            → MEDIC (own booking)
 * POST  /{id}/fail                → ADMIN / system
 * POST  /{id}/join                → PATIENT or MEDIC (own booking party)
 * POST  /{id}/heartbeat           → PATIENT or MEDIC (own booking party)
 * GET   /{id}/diagnostics         → MEDIC / ADMIN
 * GET   /{id}                     → AUTH (own booking party)
 * GET   history                   → PATIENT / MEDIC (own records)
 * PUT   /{id}/notes               → MEDIC (own booking)
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/consultations")
@RequiredArgsConstructor
public class ConsultationController {

    private final ConsultationService consultationService;
    private final MedicService medicService;

    /** MEDIC — transitions SCHEDULED → IN_PROGRESS; issues OWNER token; captures escrow. */
    @PostMapping("/start")
    @ResponseStatus(HttpStatus.CREATED)
    public ConsultationResponse start(@RequestParam UUID bookingId,
                                      @AuthenticationPrincipal AppUserDetails principal) {
        return consultationService.start(bookingId, principal.getUserId());
    }

    /** MEDIC — close room; records duration; sets release_at. */
    @PostMapping("/{id}/complete")
    public ConsultationResponse complete(@PathVariable UUID id,
                                         @AuthenticationPrincipal AppUserDetails principal,
                                         @RequestParam int durationSeconds) {
        return consultationService.complete(id, principal.getUserId(), durationSeconds);
    }

    /** ADMIN / system — mark no-show or technical failure. */
    @PostMapping("/{id}/fail")
    public ConsultationResponse markFailed(@PathVariable UUID id) {
        return consultationService.markFailed(id);
    }

    /**
     * PATIENT or MEDIC — issues a short-lived Daily.co meeting token.
     *
     * <p>Returns {@code { roomUrl, token, role, expiresAt }}. The token is valid
     * for {@code app.video.token-ttl} (default 15 min). Clients must never cache
     * it; they re-call this endpoint on page refresh.
     */
    @PostMapping("/{id}/join")
    public ResponseEntity<JoinTokenResponse> join(@PathVariable UUID id,
                                                   @AuthenticationPrincipal AppUserDetails principal) {
        JoinTokenResponse response = consultationService.joinToken(id, principal.getUserId());
        return ResponseEntity.ok(response);
    }

    /**
     * PATIENT or MEDIC — heartbeat signal sent every 30 s while a call is active.
     *
     * <p>Used as a secondary no-show signal alongside Daily webhooks to prevent
     * false-positive MEDIC_NO_SHOW classification when the webhook is delayed.
     * Returns 204 No Content on success.
     */
    @PostMapping("/{id}/heartbeat")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void heartbeat(@PathVariable UUID id,
                          @AuthenticationPrincipal AppUserDetails principal,
                          @Valid @RequestBody HeartbeatRequest request) {
        consultationService.recordHeartbeat(id, principal.getUserId(), request);
    }

    /**
     * MEDIC / ADMIN — returns the full diagnostic timeline for a consultation.
     *
     * <p>Aggregates Daily webhook events and heartbeat samples; used in the
     * dispute resolution UI to reconstruct what happened in a call.
     */
    @GetMapping("/{id}/diagnostics")
    public DiagnosticsResponse getDiagnostics(@PathVariable UUID id,
                                               @AuthenticationPrincipal AppUserDetails principal) {
        return consultationService.getDiagnostics(id, principal.getUserId());
    }

    /** AUTH — look up consultation by booking id (used by the UI booking-detail page). */
    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<ConsultationResponse> getByBookingId(
            @PathVariable UUID bookingId,
            @AuthenticationPrincipal AppUserDetails principal) {
        return ResponseEntity.ok(consultationService.getByBookingId(bookingId, principal.getUserId()));
    }

    /** AUTH — get consultation details; decrypted notes returned only to medic. */
    @GetMapping("/{id}")
    public ConsultationResponse getById(@PathVariable UUID id,
                                        @AuthenticationPrincipal AppUserDetails principal) {
        return consultationService.getById(id, principal.getUserId());
    }

    /** PATIENT — paginated consultation history. */
    @GetMapping("/my/patient")
    public Page<ConsultationResponse> getPatientHistory(@AuthenticationPrincipal AppUserDetails principal,
                                                        Pageable pageable) {
        return consultationService.getPatientHistory(principal.getUserId(), pageable);
    }

    /** MEDIC — paginated consultation history by medic entity ID. */
    @GetMapping("/my/medic")
    public Page<ConsultationResponse> getMedicHistory(@AuthenticationPrincipal AppUserDetails principal,
                                                      Pageable pageable) {
        UUID medicId = medicService.getByUserId(principal.getUserId()).id();
        return consultationService.getMedicHistory(medicId, pageable);
    }

    /** AUTH — fetch decrypted consultation notes (empty string if none saved yet). */
    @GetMapping("/{id}/notes")
    public ResponseEntity<ConsultationNoteResponse> getNotes(@PathVariable UUID id,
                                                              @AuthenticationPrincipal AppUserDetails principal) {
        return ResponseEntity.ok(consultationService.getNotes(id, principal.getUserId()));
    }

    /** MEDIC — save / update encrypted consultation notes. */
    @PutMapping("/{id}/notes")
    public ConsultationResponse saveNotes(@PathVariable UUID id,
                                          @AuthenticationPrincipal AppUserDetails principal,
                                          @Valid @RequestBody ConsultationNotesRequest request) {
        return consultationService.saveNotes(id, principal.getUserId(), request);
    }
}
