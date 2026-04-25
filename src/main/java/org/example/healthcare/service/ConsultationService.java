package org.example.healthcare.service;

import org.example.healthcare.dto.consultation.ConsultationNotesRequest;
import org.example.healthcare.dto.consultation.ConsultationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ConsultationService {

    /** Called by medic when opening the video room — transitions SCHEDULED → IN_PROGRESS */
    ConsultationResponse start(UUID bookingId, UUID medicUserId);

    /** Called when both parties leave — transitions IN_PROGRESS → COMPLETED, sets release_at */
    ConsultationResponse complete(UUID consultationId, UUID medicUserId, int durationSeconds);

    /** Called by system or admin on timeout / no-show — transitions → FAILED */
    ConsultationResponse markFailed(UUID consultationId);

    ConsultationResponse getById(UUID id, UUID principalId);

    Page<ConsultationResponse> getPatientHistory(UUID patientId, Pageable pageable);

    Page<ConsultationResponse> getMedicHistory(UUID medicId, Pageable pageable);

    /** Medic saves/updates encrypted consultation notes — only allowed in COMPLETED state */
    ConsultationResponse saveNotes(UUID consultationId, UUID medicUserId, ConsultationNotesRequest request);
}
