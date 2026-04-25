package org.example.healthcare.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.healthcare.dto.availability.AvailabilityRequest;
import org.example.healthcare.dto.availability.AvailabilityResponse;
import org.example.healthcare.service.AvailabilityService;
import org.example.healthcare.service.MedicService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AvailabilityController {

    private final AvailabilityService availabilityService;
    private final MedicService medicService;

    /**
     * GET /api/v1/medics/me/availabilities
     * Get the authenticated medic's own weekly template (MEDIC).
     */
    @GetMapping("/api/v1/medics/me/availabilities")
    public ResponseEntity<List<AvailabilityResponse>> getMyAvailabilities(
            @RequestHeader("X-User-Id") UUID userId) {
        UUID medicId = medicService.getByUserId(userId).id();
        return ResponseEntity.ok(availabilityService.getByMedic(medicId));
    }

    /**
     * POST /api/v1/medics/me/availabilities
     * Add a time window for a day (MEDIC).
     */
    @PostMapping("/api/v1/medics/me/availabilities")
    public ResponseEntity<AvailabilityResponse> create(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody AvailabilityRequest request) {
        UUID medicId = medicService.getByUserId(userId).id();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(availabilityService.create(medicId, request));
    }

    /**
     * PUT /api/v1/medics/me/availabilities/{id}
     * Update a time window (MEDIC).
     */
    @PutMapping("/api/v1/medics/me/availabilities/{id}")
    public ResponseEntity<AvailabilityResponse> update(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID id,
            @Valid @RequestBody AvailabilityRequest request) {
        UUID medicId = medicService.getByUserId(userId).id();
        return ResponseEntity.ok(availabilityService.update(medicId, id, request));
    }

    /**
     * DELETE /api/v1/medics/me/availabilities/{id}
     * Remove a time window (MEDIC).
     */
    @DeleteMapping("/api/v1/medics/me/availabilities/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID id) {
        UUID medicId = medicService.getByUserId(userId).id();
        availabilityService.delete(medicId, id);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/v1/medics/{id}/availabilities
     * Get a medic's schedule summary — patient-visible (AUTH).
     */
    @GetMapping("/api/v1/medics/{id}/availabilities")
    public ResponseEntity<List<AvailabilityResponse>> getByMedic(@PathVariable UUID id) {
        return ResponseEntity.ok(availabilityService.getByMedic(id));
    }
}
