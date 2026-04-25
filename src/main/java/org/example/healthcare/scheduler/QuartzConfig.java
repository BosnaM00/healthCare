package org.example.healthcare.scheduler;

import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Quartz trigger configuration for Phase 2 scheduled jobs.
 *
 * <p>JobDetail beans use {@code storeDurably(true)} so they survive application restarts.
 * In development the in-memory RAMJobStore is used (no quartz.properties needed).
 * For production clustering, add a {@code quartz.properties} with JDBC JobStoreTX.
 */
@Configuration
public class QuartzConfig {

    // ── PaymentReleaseJob ─────────────────────────────────────────────────────

    @Bean
    public JobDetail paymentReleaseJobDetail() {
        return JobBuilder.newJob(PaymentReleaseJob.class)
                .withIdentity("paymentReleaseJob")
                .storeDurably()
                .build();
    }

    /**
     * Runs every 15 minutes.
     * Fine-grained enough to minimise payout delay without hammering the DB.
     */
    @Bean
    public Trigger paymentReleaseTrigger(JobDetail paymentReleaseJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(paymentReleaseJobDetail)
                .withIdentity("paymentReleaseTrigger")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0/15 * * * ?"))
                .build();
    }

    // ── InvitationExpiryJob ───────────────────────────────────────────────────

    @Bean
    public JobDetail invitationExpiryJobDetail() {
        return JobBuilder.newJob(InvitationExpiryJob.class)
                .withIdentity("invitationExpiryJob")
                .storeDurably()
                .build();
    }

    /** Runs once daily at 00:05 UTC */
    @Bean
    public Trigger invitationExpiryTrigger(JobDetail invitationExpiryJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(invitationExpiryJobDetail)
                .withIdentity("invitationExpiryTrigger")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 5 0 * * ?"))
                .build();
    }
}
