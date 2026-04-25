package org.example.healthcare.model;

/**
 * State machine for clinics.status:
 *
 * PENDING_APPROVAL — Clinic submitted for admin review.
 * ACTIVE           — Clinic approved; can employ medics and receive bookings.
 * SUSPENDED        — Clinic suspended by admin; all associated medic bookings blocked.
 */
public enum ClinicStatus {
    PENDING_APPROVAL,
    ACTIVE,
    SUSPENDED
}
