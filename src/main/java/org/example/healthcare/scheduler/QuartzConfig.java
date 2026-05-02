package org.example.healthcare.scheduler;

import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Quartz trigger configuration for MediConnect scheduled jobs.
 *
 * <p>Job persistence: all jobs use {@code storeDurably(true)} so they survive restarts.
 * The JDBC JobStoreTX (PostgreSQL) is configured in {@code application.properties} and
 * the schema is initialised by Flyway migration V2.
 *
 * <p>Jobs:
 * <ul>
 *   <li>{@link AutoReleaseConsultationJob} — scans for HELD payments whose
 *       consultation.release_at has passed and releases them to the medic.</li>
 *   <li>{@link InvitationExpiryJob} — expires stale clinic invitations (daily).</li>
 *   <li>{@link ConsultationNoShowJob} — detects no-show consultations and fails them
 *       with the appropriate reason (MEDIC_NO_SHOW / PATIENT_NO_SHOW). Every 5 min.</li>
 *   <li>{@link ConsultationReminderJob} — sends T-1h reminder emails. Every 5 min.</li>
 * </ul>
 */
@Configuration
public class QuartzConfig {

    // ── AutoReleaseConsultationJob ────────────────────────────────────────────

    @Bean
    public JobDetail autoReleaseJobDetail() {
        return JobBuilder.newJob(AutoReleaseConsultationJob.class)
                .withIdentity("autoReleaseConsultationJob", "payments")
                .withDescription("Releases HELD payments after the 120-minute dispute window")
                .storeDurably()
                .build();
    }

    /**
     * Runs every 5 minutes to minimise payout delay without hammering the DB.
     * Misfire policy: MISFIRE_INSTRUCTION_FIRE_NOW so a missed fire executes immediately
     * after a restart (required by the plan document).
     */
    @Bean
    public Trigger autoReleaseTrigger(JobDetail autoReleaseJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(autoReleaseJobDetail)
                .withIdentity("autoReleaseTrigger", "payments")
                .withSchedule(CronScheduleBuilder
                        .cronSchedule("0 0/5 * * * ?")
                        .withMisfireHandlingInstructionFireAndProceed())
                .build();
    }

    // ── InvitationExpiryJob ───────────────────────────────────────────────────

    @Bean
    public JobDetail invitationExpiryJobDetail() {
        return JobBuilder.newJob(InvitationExpiryJob.class)
                .withIdentity("invitationExpiryJob", "admin")
                .storeDurably()
                .build();
    }

    /** Runs once daily at 00:05 UTC. */
    @Bean
    public Trigger invitationExpiryTrigger(JobDetail invitationExpiryJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(invitationExpiryJobDetail)
                .withIdentity("invitationExpiryTrigger", "admin")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 5 0 * * ?"))
                .build();
    }

    // ── ConsultationNoShowJob ─────────────────────────────────────────────────

    @Bean
    public JobDetail consultationNoShowJobDetail() {
        return JobBuilder.newJob(ConsultationNoShowJob.class)
                .withIdentity("consultationNoShowJob", "consultations")
                .withDescription("Auto-fails no-show consultations 10 min after scheduled start")
                .storeDurably()
                .build();
    }

    /**
     * Runs every 5 minutes — same cadence as AutoReleaseConsultationJob so the
     * no-show detection and payment release share the polling window.
     */
    @Bean
    public Trigger consultationNoShowTrigger(JobDetail consultationNoShowJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(consultationNoShowJobDetail)
                .withIdentity("consultationNoShowTrigger", "consultations")
                .withSchedule(CronScheduleBuilder
                        .cronSchedule("0 0/5 * * * ?")
                        .withMisfireHandlingInstructionFireAndProceed())
                .build();
    }

    // ── ConsultationReminderJob ───────────────────────────────────────────────

    @Bean
    public JobDetail consultationReminderJobDetail() {
        return JobBuilder.newJob(ConsultationReminderJob.class)
                .withIdentity("consultationReminderJob", "notifications")
                .withDescription("Sends T-1h reminder emails for upcoming consultations")
                .storeDurably()
                .build();
    }

    /** Runs every 5 minutes to ensure reminders fall within the 60–65 min window. */
    @Bean
    public Trigger consultationReminderTrigger(JobDetail consultationReminderJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(consultationReminderJobDetail)
                .withIdentity("consultationReminderTrigger", "notifications")
                .withSchedule(CronScheduleBuilder
                        .cronSchedule("0 0/5 * * * ?")
                        .withMisfireHandlingInstructionFireAndProceed())
                .build();
    }
}
