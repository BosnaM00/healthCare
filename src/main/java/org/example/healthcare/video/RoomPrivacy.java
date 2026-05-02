package org.example.healthcare.video;

/**
 * Access control level for a Daily.co room.
 *
 * <p>PRIVATE — token required to join (default for consultations).
 * <p>PUBLIC  — anyone with the URL can join (not used in MediConnect).
 */
public enum RoomPrivacy {
    /** Token required — no unauthenticated access. */
    PRIVATE,
    /** Anyone can join — not used in production. */
    PUBLIC
}
