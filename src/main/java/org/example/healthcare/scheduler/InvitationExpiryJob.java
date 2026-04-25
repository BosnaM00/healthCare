package org.example.healthcare.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.healthcare.service.MedicInvitationService;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

/**
 * Quartz job that bulk-expires PENDING medic invitations older than 72 hours.
 *
 * <p>Trigger: once daily at midnight (configured in {@link QuartzConfig}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InvitationExpiryJob implements Job {

    private final MedicInvitationService invitationService;

    @Override
    public void execute(JobExecutionContext context) {
        int expired = invitationService.expireStale();
        log.info("InvitationExpiryJob: expired {} stale invitations", expired);
    }
}
