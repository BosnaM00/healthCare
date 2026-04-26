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
}
