package org.example.healthcare.video;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Provider-agnostic contract for video room lifecycle management.
 *
 * <p>All business logic calls this interface. The only concrete implementation
 * for Phase 1 is {@link DailyVideoProvider}. Swapping to Twilio Video or Agora
 * requires only a new implementation class — no other code changes.
 *
 * <p>Callers: {@code ConsultationServiceImpl} only. No controller or other
 * service may import Daily SDK classes or call {@code api.daily.co} directly.
 */
public interface VideoProvider {

    /**
     * Creates a video room scoped to a single consultation.
     *
     * <p>Implementations must be <strong>idempotent on {@code consultationId}</strong>:
     * a second call with the same ID must return the existing room rather than creating
     * a duplicate.
     *
     * @param consultationId  Unique identifier used as the room name seed.
     * @param earliestJoinAt  Earliest instant participants may join (nbf for tokens).
     * @param expiresAt       Room is auto-deleted by the provider after this instant.
     * @param privacy         Access control — use {@link RoomPrivacy#PRIVATE} for consultations.
     * @return a {@link VideoRoom} with the join URL and provider-internal name.
     */
    VideoRoom createRoom(UUID consultationId,
                         Instant earliestJoinAt,
                         Instant expiresAt,
                         RoomPrivacy privacy);

    /**
     * Issues a short-lived meeting token bound to a specific participant.
     *
     * <p>Role {@link ParticipantRole#OWNER} grants the medic the ability to
     * mute/eject participants and start recording (when enabled).
     * Role {@link ParticipantRole#PARTICIPANT} gives standard participant permissions.
     *
     * @param consultationId Consultation this token is scoped to.
     * @param roomName       Provider-internal room name (from {@link VideoRoom#roomName()}).
     * @param userId         Platform user ID — used as the token subject.
     * @param displayName    Participant display name shown in the video tile.
     * @param role           {@link ParticipantRole#OWNER} for medic, PARTICIPANT for patient.
     * @param ttl            Token lifetime; typically 15 minutes.
     * @return a {@link VideoMeetingToken} containing the opaque token string and metadata.
     */
    VideoMeetingToken issueToken(UUID consultationId,
                                 String roomName,
                                 UUID userId,
                                 String displayName,
                                 ParticipantRole role,
                                 Duration ttl);

    /**
     * Deletes the room, immediately disconnecting all participants.
     *
     * <p>Called by {@code markFailed()} or admin tooling. Safe to call even if
     * the room has already expired or does not exist.
     *
     * @param roomName Provider-internal room name.
     */
    void deleteRoom(String roomName);
}
