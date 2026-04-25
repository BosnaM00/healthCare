package org.example.healthcare.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.healthcare.dto.dispute.DisputeRequest;
import org.example.healthcare.dto.dispute.DisputeResolutionRequest;
import org.example.healthcare.dto.dispute.DisputeResponse;
import org.example.healthcare.security.AppUserDetails;
import org.example.healthcare.service.DisputeService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Dispute lifecycle endpoints.
 *
 * <p>Role guards:
 * POST   open       → PATIENT (own booking)
 * PATCH  claim      → ADMIN
 * PATCH  resolve    → ADMIN
 * GET    /{id}      → AUTH (own booking party or ADMIN)
 * GET    list       → ADMIN
 */
@RestController
@RequestMapping("/api/v1/disputes")
@RequiredArgsConstructor
public class DisputeController {

    private final DisputeService disputeService;

    /** PATIENT — raise a dispute against a completed consultation */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DisputeResponse open(@AuthenticationPrincipal AppUserDetails principal,
                                @Valid @RequestBody DisputeRequest request) {
        return disputeService.open(principal.getUserId(), request);
    }

    /** ADMIN — assign dispute to self for review */
    @PatchMapping("/{id}/claim")
    public DisputeResponse claimForReview(@PathVariable java.util.UUID id,
                                          @AuthenticationPrincipal AppUserDetails principal) {
        return disputeService.claimForReview(id, principal.getUserId());
    }

    /** ADMIN — resolve dispute and trigger payment action */
    @PatchMapping("/{id}/resolve")
    public DisputeResponse resolve(@PathVariable java.util.UUID id,
                                   @AuthenticationPrincipal AppUserDetails principal,
                                   @Valid @RequestBody DisputeResolutionRequest request) {
        return disputeService.resolve(id, principal.getUserId(), request);
    }

    /** AUTH */
    @GetMapping("/{id}")
    public DisputeResponse getById(@PathVariable java.util.UUID id) {
        return disputeService.getById(id);
    }

    /** ADMIN — list disputes by status (OPEN, UNDER_REVIEW, etc.) */
    @GetMapping
    public Page<DisputeResponse> listByStatus(@RequestParam(defaultValue = "OPEN") String status,
                                              Pageable pageable) {
        return disputeService.listByStatus(status, pageable);
    }
}
