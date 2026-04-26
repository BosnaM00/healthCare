package org.example.healthcare.dto.stripe;

import org.example.healthcare.model.StripeAccountStatus;

import java.time.Instant;

/**
 * Current Stripe Connect capability flags for a medic.
 *
 * @param stripeAccountId        Stripe acct_… id
 * @param chargesEnabled         true when card charges can be accepted
 * @param payoutsEnabled         true when payouts to the medic's bank are enabled
 * @param detailsSubmitted       true when the medic has submitted all required details
 * @param accountStatus          derived overall status
 * @param requirementsCurrentlyDue JSON array of outstanding requirement keys (may be null/empty)
 * @param lastSyncedAt           when these flags were last refreshed from Stripe
 */
public record StripeAccountStatusResponse(
        String stripeAccountId,
        boolean chargesEnabled,
        boolean payoutsEnabled,
        boolean detailsSubmitted,
        StripeAccountStatus accountStatus,
        String requirementsCurrentlyDue,
        Instant lastSyncedAt
) {}
