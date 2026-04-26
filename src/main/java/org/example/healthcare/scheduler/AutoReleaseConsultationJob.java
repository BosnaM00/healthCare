package org.example.healthcare.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.healthcare.model.Payment;
import org.example.healthcare.repository.PaymentRepository;
import org.example.healthcare.service.PaymentService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Quartz job that auto-releases escrowed payments after the 120-minute dispute window.
 *
 * <p>Runs on the JDBC JobStoreTX (cluster-safe) so a single node executes the job
 * even when multiple instances are running.
 *
 * <p>Trigger: every 5 minutes (configured in {@link QuartzConfig}).
 * Misfire policy: {@code MISFIRE_INSTRUCTION_FIRE_NOW} — if a fire is missed due to
 * a restart, it fires immediately when the scheduler comes back online.
 *
 * <p>Logic:
 * <ol>
 *   <li>Query payments in HELD state whose consultation.release_at has passed.</li>
 *   <li>For each, call {@link PaymentService#release(java.util.UUID)}.</li>
 *   <li>Failures per payment are caught individually so one failure does not abort the batch.</li>
 * </ol>
 *
 * <p>Idempotency: {@link PaymentService#release} guards against double-release via
 * {@code @Version} optimistic locking plus a status check.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@DisallowConcurrentExecution  // Prevents overlapping executions on the same node
public class AutoReleaseConsultationJob implements Job {

    private final PaymentRepository paymentRepository;
    private final PaymentService    paymentService;

    @Override
    @Transactional
    public void execute(JobExecutionContext context) {
        Instant now = Instant.now();
        List<Payment> releasable = paymentRepository.findReleasablePayments(now);

        if (releasable.isEmpty()) {
            log.debug("AutoReleaseConsultationJob: no releasable payments at {}", now);
            return;
        }

        log.info("AutoReleaseConsultationJob: releasing {} payments at {}", releasable.size(), now);

        int succeeded = 0;
        int failed = 0;

        for (Payment payment : releasable) {
            try {
                paymentService.release(payment.getId());
                succeeded++;
                log.info("Released payment {} (booking {})", payment.getId(), payment.getBooking().getId());
            } catch (jakarta.persistence.OptimisticLockException ex) {
                // Another node beat us to it — this is expected in a cluster; not a real error
                log.debug("Optimistic lock for payment {} — already released by another node", payment.getId());
            } catch (Exception ex) {
                failed++;
                log.error("Failed to release payment {}: {}", payment.getId(), ex.getMessage(), ex);
            }
        }

        log.info("AutoReleaseConsultationJob done: {} released, {} failed", succeeded, failed);
    }
}
