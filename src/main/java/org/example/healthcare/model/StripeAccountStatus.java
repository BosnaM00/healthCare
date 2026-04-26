package org.example.healthcare.model;

/**
 * Onboarding / capability status of a connected Stripe Express account.
 *
 * PENDING    — Express account created; medic has not completed onboarding yet.
 * VERIFIED   — chargesEnabled AND payoutsEnabled AND detailsSubmitted = true.
 * RESTRICTED — Stripe has placed restrictions; some capabilities may be limited.
 * REJECTED   — Account deauthorised or permanently rejected by Stripe.
 */
public enum StripeAccountStatus {
    PENDING,
    VERIFIED,
    RESTRICTED,
    REJECTED
}
