package org.example.healthcare.model;

/**
 * Reason a consultation ended in the {@link ConsultationStatus#FAILED} state.
 *
 * <p>Stored as a VARCHAR on {@code consultations.failure_reason}.
 * {@code NONE} maps to a {@code NULL} column value — the application code
 * treats {@code null} as "no failure".
 */
public enum ConsultationFailureReason {

    /** Default — no failure. Column value is NULL. */
    NONE,

    /** Medic did not join within the grace period. Patient receives a full refund. */
    MEDIC_NO_SHOW,

    /** Patient did not join. Refund per policy table (§6.1 of the arch doc). */
    PATIENT_NO_SHOW,

    /** Session lasted less than 5 minutes due to a technical issue. Full refund. */
    TECHNICAL_FAILURE,

    /** Both parties agreed to cancel before or during the session. */
    MUTUAL_CANCEL,

    /** Catch-all for edge cases. */
    OTHER
}
