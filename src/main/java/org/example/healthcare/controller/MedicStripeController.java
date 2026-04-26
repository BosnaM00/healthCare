package org.example.healthcare.controller;

import lombok.RequiredArgsConstructor;
import org.example.healthcare.dto.stripe.StripeAccountStatusResponse;
import org.example.healthcare.dto.stripe.StripeOnboardingResponse;
import org.example.healthcare.security.AppUserDetails;
import org.example.healthcare.service.MedicService;
import org.example.healthcare.service.StripeConnectService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Stripe Connect endpoints for individual medics.
 *
 * <p>All endpoints require the authenticated user to have the {@code MEDIC} role.
 *
 * <ul>
 *   <li>{@code POST /api/v1/medics/me/stripe/onboarding} — start/resume onboarding</li>
 *   <li>{@code GET  /api/v1/medics/me/stripe/status}     — capability flags</li>
 *   <li>{@code GET  /api/v1/medics/me/stripe/login}      — Express Dashboard link</li>
 *   <li>{@code GET  /api/v1/medics/me/payouts}           — payout history</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/medics/me/stripe")
@RequiredArgsConstructor
public class MedicStripeController {

    private final StripeConnectService stripeConnectService;
    private final MedicService         medicService;

    /** Start or resume Stripe Express onboarding. Returns a redirect URL. */
    @PostMapping("/onboarding")
    @PreAuthorize("hasRole('MEDIC')")
    public StripeOnboardingResponse createOnboardingLink(
            @AuthenticationPrincipal AppUserDetails principal) {
        UUID medicId = resolveMedicId(principal);
        return stripeConnectService.createOnboardingLink(medicId);
    }

    /** Re-generate a fresh onboarding link (e.g. after the previous one expired). */
    @PostMapping("/onboarding/refresh")
    @PreAuthorize("hasRole('MEDIC')")
    public StripeOnboardingResponse refreshOnboardingLink(
            @AuthenticationPrincipal AppUserDetails principal) {
        UUID medicId = resolveMedicId(principal);
        return stripeConnectService.refreshOnboardingLink(medicId);
    }

    /** Get current Stripe Connect capability flags. */
    @GetMapping("/status")
    @PreAuthorize("hasRole('MEDIC')")
    public StripeAccountStatusResponse getAccountStatus(
            @AuthenticationPrincipal AppUserDetails principal) {
        UUID medicId = resolveMedicId(principal);
        return stripeConnectService.refreshAccountStatus(medicId);
    }

    /** Get a short-lived Express Dashboard login URL. */
    @GetMapping("/login")
    @PreAuthorize("hasRole('MEDIC')")
    public String loginLink(@AuthenticationPrincipal AppUserDetails principal) {
        UUID medicId = resolveMedicId(principal);
        return stripeConnectService.loginLink(medicId);
    }

    // Payout history is under a separate controller mapping (see MedicPayoutsController)

    // ── Helper ────────────────────────────────────────────────────────────────

    private UUID resolveMedicId(AppUserDetails principal) {
        return medicService.getByUserId(principal.getUserId()).id();
    }
}
