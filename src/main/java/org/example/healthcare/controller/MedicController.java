package org.example.healthcare.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.healthcare.dto.medic.MedicRequest;
import org.example.healthcare.dto.medic.MedicResponse;
import org.example.healthcare.dto.medic.MedicSearchRequest;
import org.example.healthcare.dto.medic.MedicVerificationRequest;
import org.example.healthcare.security.AppUserDetails;
import org.example.healthcare.service.MedicService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/medics")
@RequiredArgsConstructor
public class MedicController {

    private final MedicService medicService;

    /**
     * POST /api/v1/medics
     * Create a medic profile for the authenticated user (MEDIC).
     */
    @PostMapping
    public ResponseEntity<MedicResponse> createProfile(
            @AuthenticationPrincipal AppUserDetails principal,
            @Valid @RequestBody MedicRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(medicService.createProfile(principal.getUserId(), request));
    }

    /**
     * GET /api/v1/medics/me
     * Get the authenticated medic's own full profile (MEDIC).
     */
    @GetMapping("/me")
    public ResponseEntity<MedicResponse> getMe(@AuthenticationPrincipal AppUserDetails principal) {
        return ResponseEntity.ok(medicService.getByUserId(principal.getUserId()));
    }

    /**
     * PUT /api/v1/medics/me
     * Update the authenticated medic's own profile (MEDIC).
     */
    @PutMapping("/me")
    public ResponseEntity<MedicResponse> updateMe(
            @AuthenticationPrincipal AppUserDetails principal,
            @Valid @RequestBody MedicRequest request) {
        return ResponseEntity.ok(medicService.updateProfile(principal.getUserId(), request));
    }

    /**
     * PUT /api/v1/medics/me/specialties
     * Replace the authenticated medic's specialty set — idempotent (MEDIC).
     */
    @PutMapping("/me/specialties")
    public ResponseEntity<MedicResponse> updateSpecialties(
            @AuthenticationPrincipal AppUserDetails principal,
            @RequestBody List<UUID> specialtyIds) {
        MedicResponse profile = medicService.getByUserId(principal.getUserId());
        return ResponseEntity.ok(medicService.updateSpecialties(profile.id(), specialtyIds));
    }

    /**
     * GET /api/v1/medics/{id}
     * Get a public medic profile (AUTH).
     */
    @GetMapping("/{id}")
    public ResponseEntity<MedicResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(medicService.getById(id));
    }

    /**
     * GET /api/v1/medics?specialtyId=&clinicId=&instantOnly=&page=&size=
     * Search medics by specialty / clinic / instant availability (AUTH).
     */
    @GetMapping
    public ResponseEntity<Page<MedicResponse>> search(
            @ModelAttribute MedicSearchRequest request) {
        return ResponseEntity.ok(
                medicService.search(request, PageRequest.of(request.page(), request.size())));
    }

    /**
     * PATCH /api/v1/medics/{id}/verification
     * Update a medic's verification status (ADMIN).
     */
    @PatchMapping("/{id}/verification")
    public ResponseEntity<MedicResponse> updateVerification(
            @PathVariable UUID id,
            @Valid @RequestBody MedicVerificationRequest request) {
        return ResponseEntity.ok(medicService.updateVerification(id, request));
    }
}
