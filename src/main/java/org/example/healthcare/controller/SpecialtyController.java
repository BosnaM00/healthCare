package org.example.healthcare.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.healthcare.dto.specialty.SpecialtyRequest;
import org.example.healthcare.dto.specialty.SpecialtyResponse;
import org.example.healthcare.service.SpecialtyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/specialties")
@RequiredArgsConstructor
public class SpecialtyController {

    private final SpecialtyService specialtyService;

    /**
     * GET /api/v1/specialties
     * List all specialties (PUBLIC).
     */
    @GetMapping
    public ResponseEntity<List<SpecialtyResponse>> listAll() {
        return ResponseEntity.ok(specialtyService.listAll());
    }

    /**
     * GET /api/v1/specialties/{id}
     * Get a single specialty (PUBLIC).
     */
    @GetMapping("/{id}")
    public ResponseEntity<SpecialtyResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(specialtyService.getById(id));
    }

    /**
     * POST /api/v1/specialties
     * Create a specialty (ADMIN).
     */
    @PostMapping
    public ResponseEntity<SpecialtyResponse> create(@Valid @RequestBody SpecialtyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(specialtyService.create(request));
    }

    /**
     * PUT /api/v1/specialties/{id}
     * Update a specialty (ADMIN).
     */
    @PutMapping("/{id}")
    public ResponseEntity<SpecialtyResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody SpecialtyRequest request) {
        return ResponseEntity.ok(specialtyService.update(id, request));
    }

    /**
     * DELETE /api/v1/specialties/{id}
     * Delete a specialty — guarded: rejects if any medic has it assigned (ADMIN).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        specialtyService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
