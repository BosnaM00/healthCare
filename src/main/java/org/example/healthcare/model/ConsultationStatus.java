package org.example.healthcare.model;

/**
 * State machine for consultations.status:
 *
 * SCHEDULED    — Booking confirmed; video room not yet started.
 * IN_PROGRESS  — Medic opened the video room; session active.
 * COMPLETED    — Both parties left; duration_seconds recorded; triggers HELD payment.
 * FAILED       — Technical failure or no-show; triggers refund evaluation.
 * DISPUTED     — Admin opened dispute review on this consultation.
 */
public enum ConsultationStatus {
    SCHEDULED,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    DISPUTED
}
