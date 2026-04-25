package org.example.healthcare.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.healthcare.model.Consultation;
import org.example.healthcare.model.Payment;
import org.example.healthcare.model.PaymentStatus;
import org.example.healthcare.repository.ConsultationRepository;
import org.example.healthcare.repository.PaymentRepository;
import org.example.healthcare.service.PaymentService;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Quartz job that auto-releases escrowed payments after the dispute window closes.
 *
 * <p>Trigger: every 15 minutes (configured in {@link QuartzConfig}).
 *
 * <p>Logic:
 * <ol>
 *   <li>Query consultations WHERE status = COMPLETED AND release_at &lt;= now.</li>
 *   <li>For each linked payment in HELD state, call PaymentService.release().</li>
 *   <li>Errors per payment are caught individually so one failure does not abort the batch.</li>
 * </ol>
 *
 * <p>Idempotency: PaymentService.release() guards against double-release with a status check.
 *
 * <p>To enable Quartz cluster persistence, configure quartz.properties with
 * {@code org.quartz.jobStore.class=org.quartz.impl.jdbcjobstore.JobStoreTX}
 * and ensure the Quartz schema tables are present in your PostgreSQL database.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReleaseJob implements Job {

    private final ConsultationRepository consultationRepository;
    private final PaymentRepository      paymentRepository;
    private final PaymentService         paymentService;

    @Override
    @Transactional
    public void execute(JobExecutionContext context) {
        Instant now = Instant.now();
        List<Consultation> releasable = consultationRepository.findReleasable(now);

        log.info("PaymentReleaseJob: found {} releasable consultations at {}", releasable.size(), now);

        for (Consultation consultation : releasable) {
            try {
                paymentRepository.findByBookingId(consultation.getBooking().getId())
                        .filter(p -> p.getStatus() == PaymentStatus.HELD)
                        .ifPresent(p -> {
                            paymentService.release(p.getId());
                            log.info("Released payment {} for consultation {}", p.getId(), consultation.getId());
                        });
            } catch (Exception ex) {
                log.error("Failed to release payment for consultation {}: {}",
                        consultation.getId(), ex.getMessage(), ex);
            }
        }
    }
}
