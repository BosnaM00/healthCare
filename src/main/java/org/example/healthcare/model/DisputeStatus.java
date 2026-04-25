package org.example.healthcare.model;

/**
 * State machine for disputes.status:
 *
 * OPEN                — Dispute submitted; awaiting admin assignment.
 * UNDER_REVIEW        — Admin is actively investigating (48 h SLA).
 * RESOLVED_RELEASED   — Admin ruled in medic's favour; payment released.
 * RESOLVED_REFUNDED   — Admin ruled in patient's favour; full refund issued.
 * RESOLVED_PARTIAL    — Partial split: platform keeps a fraction, rest refunded.
 */
public enum DisputeStatus {
    OPEN,
    UNDER_REVIEW,
    RESOLVED_RELEASED,
    RESOLVED_REFUNDED,
    RESOLVED_PARTIAL
}
