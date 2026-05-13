package org.example.healthcare.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.healthcare.model.Consultation;
import org.example.healthcare.model.ConsultationFailureReason;
import org.example.healthcare.model.ConsultationStatus;
import org.example.healthcare.repository.ConsultationRepository;
import org.example.healthcare.repository.VideoSessionEventRepository;
import org.example.healthcare.service.ConsultationService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Quartz job that detects and auto-fails no-show consultations.
 *
 * <p>Fires every 5 minutes. For each SCHEDULED consultation whose slot start
 * time was more than 10 minutes ago:
 *
 * <ul>
 *   <li>If no {@code participant.joined} webhook was received for the medic
 *       → {@link ConsultationFailureReason#MEDIC_NO_SHOW}. Full refund issued.</li>
 *   <li>If the medic joined but the patient did not
 *       → {@link ConsultationFailureReason#PATIENT_NO_SHOW}.</li>
 * </ul>
 *
 * <p>Two-signal requirement (per arch doc §5.6):
 * A no-show is only recorded when BOTH conditions hold:
 * (a) No {@code participant.joined} webhook for the party, AND
 * (b) No {@code heartbeat} received from that party.
 * This prevents false positives when Daily's webhook delivery is delayed.
 */
@Slf4j
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class ConsultationNoShowJob implements Job {

    /** Grace period after scheduled start before declaring a no-show (10 minutes). */
    private static final long NO_SHOW_GRACE_MINUTES = 10L;

    private final ConsultationRepository      consultationRepository;
    private final VideoSessionEventRepository videoSessionEventRepository;
    private final ConsultationService         consultationService;

    @Override
    public void execute(JobExecutionContext context) {
        Instant now         = Instant.now();
        Instant graceCutoff = now.minus(NO_SHOW_GRACE_MINUTES, ChronoUnit.MINUTES);

        // Find SCHEDULED consultations whose slot start is past the grace period.
        // Uses a JOIN FETCH query so booking/slot/medic/patient are loaded in one query,
        // avoiding LazyInitializationException when accessed outside the repo session.
        List<Consultation> candidates = consultationRepository
                .findByStatusWithAssociations(ConsultationStatus.SCHEDULED)
                .stream()
                .filter(c -> {
                    Instant slotStart = c.getBooking().getSlot().getStartsAt();
                    return slotStart != null && slotStart.isBefore(graceCutoff);
                })
                .toList();

        for (Consultation consultation : candidates) {
            try {
                processNoShowCandidate(consultation);
            } catch (Exception e) {
                log.error("ConsultationNoShowJob: error processing consultation {}: {}",
                        consultation.getId(), e.getMessage(), e);
            }
        }

        if (!candidates.isEmpty()) {
            log.info("ConsultationNoShowJob completed: {} candidates evaluated", candidates.size());
        }
    }

    private void processNoShowCandidate(Consultation consultation) {
        UUID consultationId = consultation.getId();

        // Check for medic's participant.joined event (webhook signal)
        UUID medicUserId = consultation.getBooking().getMedic().getUser().getId();
        boolean medicJoinedWebhook = videoSessionEventRepository
                .existsByConsultationIdAndEventTypeAndActorUserId(
                        consultationId, "participant.joined", medicUserId);

        // Check for medic's heartbeat (secondary signal)
        boolean medicHeartbeat = videoSessionEventRepository
                .existsByConsultationIdAndEventTypeAndActorUserId(
                        consultationId, "heartbeat", medicUserId);

        // Both signals absent → MEDIC_NO_SHOW
        if (!medicJoinedWebhook && !medicHeartbeat) {
            log.warn("ConsultationNoShowJob: marking MEDIC_NO_SHOW for consultation {}", consultationId);
            consultationService.markFailed(consultationId, ConsultationFailureReason.MEDIC_NO_SHOW);
            return;
        }

        // Medic joined but patient did not → PATIENT_NO_SHOW
        UUID patientUserId = consultation.getBooking().getPatient().getId();
        boolean patientJoinedWebhook = videoSessionEventRepository
                .existsByConsultationIdAndEventTypeAndActorUserId(
                        consultationId, "participant.joined", patientUserId);
        boolean patientHeartbeat = videoSessionEventRepository
                .existsByConsultationIdAndEventTypeAndActorUserId(
                        consultationId, "heartbeat", patientUserId);

        if (!patientJoinedWebhook && !patientHeartbeat) {
            log.warn("ConsultationNoShowJob: marking PATIENT_NO_SHOW for consultation {}", consultationId);
            consultationService.markFailed(consultationId, ConsultationFailureReason.PATIENT_NO_SHOW);
        }
    }
}
