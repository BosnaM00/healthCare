package org.example.healthcare.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.healthcare.dto.clinic.ClinicRequest;
import org.example.healthcare.dto.clinic.ClinicResponse;
import org.example.healthcare.dto.clinic.ClinicStatusRequest;
import org.example.healthcare.service.ClinicService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Clinic management.
 *
 * <p>Role guards (enforced by SecurityConfig, stubs here):
 * POST / PATCH status → ADMIN
 * PUT               → ADMIN
 * GET               → AUTH (any authenticated user)
 */
@RestController
@RequestMapping("/api/v1/clinics")
@RequiredArgsConstructor
public class ClinicController {

    private final ClinicService clinicService;

    /** ADMIN — register a new clinic */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClinicResponse create(@Valid @RequestBody ClinicRequest request) {
        return clinicService.create(request);
    }

    /** AUTH — public clinic directory */
    @GetMapping
    public Page<ClinicResponse> listAll(Pageable pageable) {
        return clinicService.listAll(pageable);
    }

    /** AUTH */
    @GetMapping("/{id}")
    public ClinicResponse getById(@PathVariable UUID id) {
        return clinicService.getById(id);
    }

    /** ADMIN */
    @PutMapping("/{id}")
    public ClinicResponse update(@PathVariable UUID id, @Valid @RequestBody ClinicRequest request) {
        return clinicService.update(id, request);
    }

    /** ADMIN — approve / suspend */
    @PatchMapping("/{id}/status")
    public ClinicResponse updateStatus(@PathVariable UUID id,
                                       @Valid @RequestBody ClinicStatusRequest request) {
        return clinicService.updateStatus(id, request);
    }

    /** ADMIN — link Stripe Connect account after onboarding */
    @PatchMapping("/{id}/stripe-account")
    public ClinicResponse setStripeAccount(@PathVariable UUID id,
                                           @RequestParam String stripeAccountId) {
        return clinicService.setStripeAccount(id, stripeAccountId);
    }
}
