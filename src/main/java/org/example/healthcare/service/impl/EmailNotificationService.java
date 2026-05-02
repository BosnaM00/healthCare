package org.example.healthcare.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.healthcare.service.NotificationService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Stub implementation of {@link NotificationService} for Phase 1.
 *
 * <p>Logs all notification events to SLF4J instead of sending real emails.
 * Replace or extend this class with a SendGrid or JavaMail implementation
 * once email provider credentials are provisioned (see implementation plan §9.1).
 *
 * <p>Templates live in {@code src/main/resources/templates/email/} (Thymeleaf or
 * Mustache — not yet wired in this stub).
 */
@Slf4j
@Service
public class EmailNotificationService implements NotificationService {

    @Override
    public void sendConsultationReminder(String patientEmail,
                                         String medicEmail,
                                         UUID consultationId,
                                         Instant scheduledStart,
                                         String roomUrl) {
        log.info("[NOTIFICATION STUB] Consultation reminder — patient={} medic={} " +
                 "consultationId={} scheduledStart={} roomUrl={}",
                patientEmail, medicEmail, consultationId, scheduledStart, roomUrl);
        // TODO: Replace with SendGrid / JavaMail template render + send
    }

    @Override
    public void sendBookingConfirmation(String patientEmail,
                                        UUID bookingId,
                                        UUID consultationId,
                                        Instant scheduledStart,
                                        String roomUrl) {
        log.info("[NOTIFICATION STUB] Booking confirmation — patient={} bookingId={} " +
                 "consultationId={} scheduledStart={} roomUrl={}",
                patientEmail, bookingId, consultationId, scheduledStart, roomUrl);
        // TODO: Replace with SendGrid / JavaMail template render + send
    }

    @Override
    public void sendPostCallSummary(String patientEmail,
                                    UUID consultationId,
                                    String prescriptionS3Key) {
        log.info("[NOTIFICATION STUB] Post-call summary — patient={} consultationId={} prescriptionS3Key={}",
                patientEmail, consultationId, prescriptionS3Key);
        // TODO: Replace with SendGrid / JavaMail template render + send
    }
}
