package org.example.healthcare.service;

import org.example.healthcare.dto.consultation.ConsultationNoteResponse;
import org.example.healthcare.dto.consultation.ConsultationNotesRequest;
import org.example.healthcare.dto.consultation.ConsultationResponse;
import org.example.healthcare.dto.consultation.DiagnosticsResponse;
import org.example.healthcare.dto.consultation.HeartbeatRequest;
import org.example.healthcare.dto.consultation.JoinTokenResponse;
import org.example.healthcare.model.ConsultationFailureReason;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ConsultationService {

    /**
     * Called when a booking is confirmed — creates the Consultation in SCHEDULED status
     * and provisions a video room via VideoProvider.
     * Idempotent: safe to call again if the consultation already exists.
     */
    ConsultationResponse createForBooking(UUID bookingId);

    /**
     * Called by the medic when opening the video room.
     * Requires status SCHEDULED. Transitions to IN_PROGRESS, issues OWNER token,
     * and captures the Stripe escrow payment.
     */
    ConsultationResponse start(UUID bookingId, UUID medicUserId);

    /**
     * Issues a short-lived meeting token for the authenticated participant.
     * Validates that the principal is either the booked patient or the medic.
     * Token TTL defaults to {@code app.video.token-ttl} (15 min).
     *
     * @return {@link JoinTokenResponse} with roomUrl, token, role, and expiresAt.
     */
    JoinTokenResponse joinToken(UUID consultationId, UUID principalUserId);

    /**
     * Records a client heartbeat sample for the active consultation.
     * Used as a secondary no-show signal alongside Daily webhooks.
     *
     * @param consultationId The active consultation.
     * @param principalUserId The authenticated user sending the heartbeat.
     * @param request Heartbeat payload (timestamp, RTT, media state).
     */
    void recordHeartbeat(UUID consultationId, UUID principalUserId, HeartbeatRequest request);

    /**
     * Returns the full diagnostic timeline for a consultation.
     * Accessible to MEDIC and ADMIN roles; used for dispute resolution.
     */
    DiagnosticsResponse getDiagnostics(UUID consultationId, UUID principalUserId);

    /** Called when both parties leave — transitions IN_PROGRESS → COMPLETED, sets release_at. */
    ConsultationResponse complete(UUID consultationId, UUID medicUserId, int durationSeconds);

    /**
     * Transitions the consultation to FAILED with a specific reason.
     * Triggers automatic refund for MEDIC_NO_SHOW and TECHNICAL_FAILURE.
     */
    ConsultationResponse markFailed(UUID consultationId, ConsultationFailureReason reason);

    /** Legacy overload — defaults reason to NONE (used by existing controller). */
    ConsultationResponse markFailed(UUID consultationId);

    ConsultationResponse getById(UUID id, UUID principalId);

    ConsultationResponse getByBookingId(UUID bookingId, UUID principalId);

    Page<ConsultationResponse> getPatientHistory(UUID patientId, Pageable pageable);

    Page<ConsultationResponse> getMedicHistory(UUID medicId, Pageable pageable);

    /** Medic or authorised party — fetches decrypted notes for a consultation. */
    ConsultationNoteResponse getNotes(UUID consultationId, UUID principalId);

    /** Medic saves/updates encrypted consultation notes — only allowed in COMPLETED state. */
    ConsultationResponse saveNotes(UUID consultationId, UUID medicUserId, ConsultationNotesRequest request);
}
