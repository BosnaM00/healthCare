package org.example.healthcare.video;

/**
 * Participant role inside a Daily.co video room.
 *
 * <p>OWNER — the medic; can mute/eject others and (when enabled) start recording.
 * <p>PARTICIPANT — the patient; standard participant permissions.
 */
public enum ParticipantRole {
    OWNER,
    PARTICIPANT
}
