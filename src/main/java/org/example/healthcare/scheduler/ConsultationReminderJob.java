package org.example.healthcare.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.healthcare.model.Consultation;
import org.example.healthcare.model.ConsultationStatus;
import org.example.healthcare.repository.ConsultationRepository;
import org.example.healthcare.service.NotificationService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Quartz job that sends T-1h consultation reminders via email.
 *
 * <p>Fires every 5 minutes and queries for SCHEDULED consultations whose
 * slot start time falls in the 60–65 minute window from now. Sends:
 * <ul>
 *   <li>A reminder email to the patient with the join link.</li>
 *   <li>A reminder email to the medic.</li>
 * </ul>
 *
 * <p>This job delegates to {@link NotificationService} which, in Phase 1,
 * logs to SLF4J only. The SendGrid implementation is wired in once API keys
 * are provisioned.
 */
@Slf4j
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class ConsultationReminderJob implements Job {

    /** Window start: consultations starting in 60–65 minutes receive a reminder. */
    private static final long WINDOW_MIN_MINUTES = 60L;
    private static final long WINDOW_MAX_MINUTES = 65L;

    private final ConsultationRepository consultationRepository;
    private final NotificationService    notificationService;

    @Override
    public void execute(JobExecutionContext context) {
        Instant now        = Instant.now();
        Instant windowStart = now.plus(WINDOW_MIN_MINUTES, ChronoUnit.MINUTES);
        Instant windowEnd   = now.plus(WINDOW_MAX_MINUTES, ChronoUnit.MINUTES);

        // Find SCHEDULED consultations whose slot starts within the reminder window
        List<Consultation> upcoming = consultationRepository
                .findByStatus(ConsultationStatus.SCHEDULED)
                .stream()
                .filter(c -> {
                    Instant slotStart = c.getBooking().getSlot().getStartsAt();
                    return slotStart != null
                            && slotStart.isAfter(windowStart)
                            && slotStart.isBefore(windowEnd);
                })
                .toList();

        for (Consultation consultation : upcoming) {
            try {
                sendReminderForConsultation(consultation);
            } catch (Exception e) {
                log.error("ConsultationReminderJob: failed to send reminder for consultation {}: {}",
                        consultation.getId(), e.getMessage(), e);
            }
        }

        if (!upcoming.isEmpty()) {
            log.info("ConsultationReminderJob: sent {} reminder(s)", upcoming.size());
        }
    }

    private void sendReminderForConsultation(Consultation consultation) {
        String patientEmail = consultation.getBooking().getPatient().getEmail();
        String medicEmail   = consultation.getBooking().getMedic().getUser().getEmail();
        Instant slotStart   = consultation.getBooking().getSlot().getStartsAt();
        String roomUrl      = consultation.getVideoRoomUrl();

        notificationService.sendConsultationReminder(
                patientEmail, medicEmail, consultation.getId(), slotStart, roomUrl);
    }
}
