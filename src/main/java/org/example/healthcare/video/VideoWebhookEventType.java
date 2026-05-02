package org.example.healthcare.video;

/**
 * Mirror of Daily.co webhook event type strings.
 *
 * <p>These values are matched against the {@code event.action} field in the
 * inbound JSON payload from Daily. Unknown types are silently ignored by the
 * dispatcher to support forward-compatibility with new Daily event types.
 */
public enum VideoWebhookEventType {

    /** Daily room has been started (first participant joined). */
    MEETING_STARTED("meeting.started"),

    /** A participant has joined the room. */
    PARTICIPANT_JOINED("participant.joined"),

    /** A participant has left the room. */
    PARTICIPANT_LEFT("participant.left"),

    /** All participants have left; room is empty. */
    MEETING_ENDED("meeting.ended"),

    /** A cloud recording is ready for download (Phase 3). */
    RECORDING_READY("recording.ready");

    private final String dailyEventType;

    VideoWebhookEventType(String dailyEventType) {
        this.dailyEventType = dailyEventType;
    }

    public String getDailyEventType() {
        return dailyEventType;
    }

    /**
     * Resolves a Daily event type string to the enum constant,
     * or returns {@code null} if the type is not handled.
     */
    public static VideoWebhookEventType fromDailyType(String type) {
        if (type == null) return null;
        for (VideoWebhookEventType v : values()) {
            if (v.dailyEventType.equals(type)) return v;
        }
        return null;
    }
}
