package org.example.healthcare.model;

/**
 * State machine for medic_invitations.status:
 *
 * PENDING   — Clinic manager sent the invite; token is valid.
 * ACCEPTED  — Medic registered with the token; medics.clinic_id set.
 * EXPIRED   — Token TTL elapsed (72 h) without acceptance.
 */
public enum InvitationStatus {
    PENDING,
    ACCEPTED,
    EXPIRED
}
