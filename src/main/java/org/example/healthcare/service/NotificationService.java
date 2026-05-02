package org.example.healthcare.service;

import java.time.Instant;
import java.util.UUID;

/**
 * Notification service contract for MediConnect transactional emails.
 *
 * <p>Phase 1: the only implementation ({@link impl.EmailNotificationService})
 * logs to SLF4J. SendGrid wiring is introduced once API keys are provisioned
 * (see implementation plan §9.1).
 *
 * <p>All methods are fire-and-forget — implementations must not throw checked
 * exceptions. Failures are caught and logged internally.
 */
public interface NotificationService {

    /**
     * Sends a T-1h reminder email to both the patient and the medic.
     *
     * @param patientEmail   Recipient email for the patient.
     * @param medicEmail     Recipient email for the medic.
     * @param consultationId Consultation identifier — used to build the join deep link.
     * @param scheduledStart UTC instant of the scheduled slot start.
     * @param roomUrl        Full video room URL — included in the patient email.
     */
    void sendConsultationReminder(String patientEmail,
                                  String medicEmail,
                                  UUID consultationId,
                                  Instant scheduledStart,
                                  String roomUrl);

    /**
     * Sends a booking confirmation email to the patient including the
     * join URL placeholder and cancellation policy summary.
     *
     * @param patientEmail   Recipient email.
     * @param bookingId      Booking identifier.
     * @param consultationId Associated consultation identifier.
     * @param scheduledStart UTC instant of the scheduled slot start.
     * @param roomUrl        Video room join URL (may be null if room not yet created).
     */
    void sendBookingConfirmation(String patientEmail,
                                 UUID bookingId,
                                 UUID consultationId,
                                 Instant scheduledStart,
                                 String roomUrl);

    /**
     * Sends a post-call summary email to the patient with a link to view
     * their prescription (PDF link included when available).
     *
     * @param patientEmail      Recipient email.
     * @param consultationId    Completed consultation identifier.
     * @param prescriptionS3Key S3 key of the prescription PDF (may be null).
     */
    void sendPostCallSummary(String patientEmail,
                             UUID consultationId,
                             String prescriptionS3Key);
}
