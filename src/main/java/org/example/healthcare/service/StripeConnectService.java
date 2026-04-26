package org.example.healthcare.service;

import org.example.healthcare.dto.stripe.StripeAccountStatusResponse;
import org.example.healthcare.dto.stripe.StripeOnboardingResponse;

import java.util.UUID;

public interface StripeConnectService {

    /**
     * Creates a Stripe Express account for the given medic (if one does not already exist)
     * and returns an Account Link URL for the onboarding flow.
     *
     * @param medicId the medic's UUID
     * @return onboarding URL to redirect the browser to
     */
    StripeOnboardingResponse createOnboardingLink(UUID medicId);

    /**
     * Re-generates a fresh Account Link URL for a medic who was redirected to the refresh URL.
     * Use when the original link expired.
     */
    StripeOnboardingResponse refreshOnboardingLink(UUID medicId);

    /**
     * Queries Stripe for the current account status and updates the local
     * {@code MedicStripeAccount} flags.
     *
     * @param medicId the medic's UUID
     * @return current capability flags
     */
    StripeAccountStatusResponse refreshAccountStatus(UUID medicId);

    /**
     * Returns a short-lived Express Dashboard login link for the medic.
     * Only works if the account is fully verified.
     */
    String loginLink(UUID medicId);

    /**
     * Handles the {@code account.updated} Connect webhook: refreshes flags from the
     * received account object without making an extra Stripe API call.
     *
     * @param stripeAccountId the Stripe account id from the webhook payload
     * @param chargesEnabled  from {@code account.charges_enabled}
     * @param payoutsEnabled  from {@code account.payouts_enabled}
     * @param detailsSubmitted from {@code account.details_submitted}
     * @param requirementsDueJson JSON array of currently-due requirements
     */
    void syncAccountFromWebhook(String stripeAccountId,
                                boolean chargesEnabled,
                                boolean payoutsEnabled,
                                boolean detailsSubmitted,
                                String requirementsDueJson);

    /**
     * Marks the medic's Stripe account as REJECTED (fired on
     * {@code account.application.deauthorized}).
     */
    void deauthorizeAccount(String stripeAccountId);
}
