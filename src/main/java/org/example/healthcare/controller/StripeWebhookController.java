package org.example.healthcare.controller;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.*;
import com.stripe.net.Webhook;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.healthcare.config.StripeConfig;
import org.example.healthcare.model.*;
import org.example.healthcare.repository.PaymentRepository;
import org.example.healthcare.repository.WebhookEventRepository;
import org.example.healthcare.service.AuditLogService;
import org.example.healthcare.service.StripeConnectService;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

/**
 * Stripe webhook receiver at {@code POST /api/v1/webhooks/stripe}.
 *
 * <p>Security:
 * <ul>
 *   <li>The endpoint is public (no JWT) but validates the {@code Stripe-Signature} header
 *       against the raw body using {@link Webhook#constructEvent}.</li>
 *   <li>The raw body is read via {@code HttpServletRequest.getInputStream()} —
 *       Spring must NOT deserialise it first.</li>
 *   <li>Tolerance window: 300 seconds (Stripe default).</li>
 * </ul>
 *
 * <p>Idempotency: every event is persisted to {@code webhook_events} before handling.
 * If the row already exists with status PROCESSED, the handler returns 200 immediately
 * without re-processing.
 *
 * <p>Error handling: if the handler throws, the exception propagates and Spring returns 500
 * so Stripe retries the delivery. We intentionally do NOT swallow exceptions.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
public class StripeWebhookController {

    private final StripeConfig              stripeConfig;
    private final WebhookEventRepository    webhookEventRepository;
    private final PaymentRepository         paymentRepository;
    private final AuditLogService           auditLogService;
    private final StripeConnectService      stripeConnectService;

    /** Diagnostic endpoint for operators — shows last event timestamp per type. */
    @GetMapping("/stripe/health")
    public Object health() {
        return webhookEventRepository.findLatestReceivedAtByType();
    }

    /**
     * Main webhook receiver.
     *
     * <p>IMPORTANT: {@code @RequestBody byte[]} ensures Spring reads the raw body
     * without any deserialization, which is required for Stripe signature validation.
     */
    @PostMapping(value = "/stripe", consumes = "application/json")
    @Transactional
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody byte[] rawBody,
            @RequestHeader("Stripe-Signature") String sigHeader,
            HttpServletRequest request) throws IOException {

        // 1. Verify signature on raw bytes
        String payload = new String(rawBody, StandardCharsets.UTF_8);
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, stripeConfig.webhookSecret());
        } catch (SignatureVerificationException e) {
            log.warn("Stripe webhook signature verification failed: {}", e.getMessage());
            return ResponseEntity.status(400).body("Invalid signature");
        }

        String eventId   = event.getId();
        String eventType = event.getType();

        // 2. Idempotency gate — persist event; short-circuit if already processed
        if (webhookEventRepository.existsByStripeEventId(eventId)) {
            WebhookEvent existing = webhookEventRepository.findById(eventId).orElse(null);
            if (existing != null && existing.getStatus() == WebhookEventStatus.PROCESSED) {
                log.debug("Duplicate webhook event {} ({}) — already processed; returning 200", eventId, eventType);
                return ResponseEntity.ok("Already processed");
            }
        }

        WebhookEvent webhookEvent = webhookEventRepository.findById(eventId)
                .orElseGet(() -> webhookEventRepository.save(
                        WebhookEvent.builder()
                                .stripeEventId(eventId)
                                .type(eventType)
                                .status(WebhookEventStatus.PENDING)
                                .build()));

        webhookEvent.setAttempts(webhookEvent.getAttempts() + 1);

        // 3. Dispatch by event type — DO NOT swallow exceptions (Stripe will retry)
        try {
            dispatch(event);
            webhookEvent.setStatus(WebhookEventStatus.PROCESSED);
            webhookEvent.setProcessedAt(Instant.now());
        } catch (Exception e) {
            webhookEvent.setStatus(WebhookEventStatus.FAILED);
            webhookEvent.setErrorMessage(e.getMessage());
            webhookEventRepository.save(webhookEvent);
            log.error("Webhook handler failed for event {} ({}): {}", eventId, eventType, e.getMessage(), e);
            throw e; // Let Stripe retry — return 500
        }

        webhookEventRepository.save(webhookEvent);
        return ResponseEntity.ok("Handled");
    }

    // ── Dispatch ──────────────────────────────────────────────────────────────

    private void dispatch(Event event) {
        // Note: we log only event id + type, never the full payload (GDPR / PAN safety)
        log.info("Processing Stripe event {} type={}", event.getId(), event.getType());

        switch (event.getType()) {
            case "payment_intent.succeeded"      -> handlePaymentIntentSucceeded(event);
            case "payment_intent.payment_failed" -> handlePaymentIntentFailed(event);
            case "payment_intent.canceled"       -> handlePaymentIntentCanceled(event);
            case "charge.refunded"               -> handleChargeRefunded(event);
            case "charge.dispute.created"        -> handleDisputeCreated(event);
            case "charge.dispute.closed"         -> handleDisputeClosed(event);
            case "transfer.created"              -> handleTransferCreated(event);
            case "transfer.reversed"             -> handleTransferReversed(event);
            case "account.updated"               -> handleAccountUpdated(event);
            case "account.application.deauthorized" -> handleAccountDeauthorized(event);
            case "payout.paid"                   -> handlePayoutPaid(event);
            case "payout.failed"                 -> handlePayoutFailed(event);
            default -> log.debug("Unhandled Stripe event type: {}", event.getType());
        }
    }

    // ── Event handlers ────────────────────────────────────────────────────────

    /** payment_intent.succeeded → RESERVED → HELD; persist charge_id. */
    private void handlePaymentIntentSucceeded(Event event) {
        PaymentIntent pi = (PaymentIntent) event.getDataObjectDeserializer()
                .getObject().orElseThrow(() -> new IllegalStateException("No PI in event " + event.getId()));

        paymentRepository.findByStripePaymentIntentId(pi.getId()).ifPresentOrElse(payment -> {
            if (payment.getStatus() != PaymentStatus.RESERVED) {
                log.info("payment_intent.succeeded for {} — payment already in state {}, skipping",
                        pi.getId(), payment.getStatus());
                return;
            }
            // Extract the latest charge id from the PI's charges
            String chargeId = Optional.ofNullable(pi.getLatestCharge()).orElse(null);
            payment.setStripeChargeId(chargeId);
            transitionTo(payment, PaymentStatus.RESERVED, PaymentStatus.HELD);
            paymentRepository.save(payment);

            auditLogService.log(null, "PAYMENT_HELD", "Payment", payment.getId(),
                    "status=RESERVED", "status=HELD,chargeId=" + chargeId, null);
            log.info("Payment {} transitioned RESERVED→HELD (PI={}, charge={})",
                    payment.getId(), pi.getId(), chargeId);
        }, () -> log.warn("payment_intent.succeeded for unknown PI {}", pi.getId()));
    }

    /** payment_intent.payment_failed → RESERVED → FAILED. */
    private void handlePaymentIntentFailed(Event event) {
        PaymentIntent pi = (PaymentIntent) event.getDataObjectDeserializer()
                .getObject().orElseThrow(() -> new IllegalStateException("No PI in event " + event.getId()));

        paymentRepository.findByStripePaymentIntentId(pi.getId()).ifPresentOrElse(payment -> {
            if (payment.getStatus() == PaymentStatus.RESERVED) {
                payment.setStatus(PaymentStatus.FAILED);
                paymentRepository.save(payment);
                auditLogService.log(null, "PAYMENT_FAILED", "Payment", payment.getId(),
                        "status=RESERVED", "status=FAILED", null);
                log.info("Payment {} FAILED (PI={})", payment.getId(), pi.getId());
            }
        }, () -> log.warn("payment_intent.payment_failed for unknown PI {}", pi.getId()));
    }

    /** payment_intent.canceled → RESERVED → FAILED. */
    private void handlePaymentIntentCanceled(Event event) {
        PaymentIntent pi = (PaymentIntent) event.getDataObjectDeserializer()
                .getObject().orElseThrow(() -> new IllegalStateException("No PI in event " + event.getId()));

        paymentRepository.findByStripePaymentIntentId(pi.getId()).ifPresentOrElse(payment -> {
            if (payment.getStatus() == PaymentStatus.RESERVED) {
                payment.setStatus(PaymentStatus.FAILED);
                paymentRepository.save(payment);
                log.info("Payment {} CANCELED (PI={})", payment.getId(), pi.getId());
            }
        }, () -> log.warn("payment_intent.canceled for unknown PI {}", pi.getId()));
    }

    /** charge.refunded → update refundId; transition to REFUNDED if full refund. */
    private void handleChargeRefunded(Event event) {
        Charge charge = (Charge) event.getDataObjectDeserializer()
                .getObject().orElseThrow(() -> new IllegalStateException("No charge in event " + event.getId()));

        paymentRepository.findByStripeChargeId(charge.getId()).ifPresentOrElse(payment -> {
            // Get the latest refund id from the charge's refunds list
            String refundId = charge.getRefunds() != null && !charge.getRefunds().getData().isEmpty()
                    ? charge.getRefunds().getData().get(0).getId()
                    : null;
            payment.setStripeRefundId(refundId);

            // Transition to REFUNDED if fully refunded
            if (Boolean.TRUE.equals(charge.getRefunded())) {
                payment.setStatus(PaymentStatus.REFUNDED);
                payment.setRefundedAt(Instant.now());
                auditLogService.log(null, "PAYMENT_REFUNDED", "Payment", payment.getId(),
                        "status=" + payment.getStatus(), "status=REFUNDED,refundId=" + refundId, null);
            }
            paymentRepository.save(payment);
            log.info("Charge {} refunded — payment {} refundId={}", charge.getId(), payment.getId(), refundId);
        }, () -> {
            // Fallback: look up by PI id if charge id not found
            if (charge.getPaymentIntent() != null) {
                paymentRepository.findByStripePaymentIntentId(charge.getPaymentIntent())
                        .ifPresent(payment -> {
                            payment.setStripeChargeId(charge.getId());
                            paymentRepository.save(payment);
                        });
            }
            log.warn("charge.refunded for unknown charge {} — no payment matched", charge.getId());
        });
    }

    /** charge.dispute.created → transition to DISPUTED; halt any pending release. */
    private void handleDisputeCreated(Event event) {
        com.stripe.model.Dispute dispute = (com.stripe.model.Dispute) event.getDataObjectDeserializer()
                .getObject().orElseThrow(() -> new IllegalStateException("No dispute in event " + event.getId()));

        // Find payment by charge id
        String chargeId = dispute.getCharge();
        paymentRepository.findByStripeChargeId(chargeId).ifPresentOrElse(payment -> {
            payment.setStripeDisputeId(dispute.getId());
            if (payment.getStatus() == PaymentStatus.HELD) {
                payment.setStatus(PaymentStatus.DISPUTED);
                auditLogService.log(null, "PAYMENT_DISPUTED", "Payment", payment.getId(),
                        "status=HELD", "status=DISPUTED,disputeId=" + dispute.getId(), null);
                log.warn("Payment {} DISPUTED — dispute {} opened", payment.getId(), dispute.getId());
            }
            paymentRepository.save(payment);
        }, () -> log.warn("charge.dispute.created for unknown charge {}", chargeId));
    }

    /** charge.dispute.closed → reconcile per outcome. */
    private void handleDisputeClosed(Event event) {
        com.stripe.model.Dispute dispute = (com.stripe.model.Dispute) event.getDataObjectDeserializer()
                .getObject().orElseThrow(() -> new IllegalStateException("No dispute in event " + event.getId()));

        paymentRepository.findByStripeChargeId(dispute.getCharge()).ifPresentOrElse(payment -> {
            String status = dispute.getStatus(); // "won", "lost", "warning_closed", etc.
            if ("won".equals(status)) {
                // Platform / medic won — release funds
                if (payment.getStatus() == PaymentStatus.DISPUTED) {
                    payment.setStatus(PaymentStatus.RELEASED);
                    payment.setReleasedAt(Instant.now());
                }
            } else if ("lost".equals(status)) {
                // Patient won — refund
                if (payment.getStatus() == PaymentStatus.DISPUTED) {
                    payment.setStatus(PaymentStatus.REFUNDED);
                    payment.setRefundedAt(Instant.now());
                }
            }
            paymentRepository.save(payment);
            auditLogService.log(null, "DISPUTE_CLOSED", "Payment", payment.getId(),
                    "status=DISPUTED", "status=" + payment.getStatus() + ",outcome=" + status, null);
            log.info("Dispute {} closed (outcome={}) — payment {} → {}", dispute.getId(), status,
                    payment.getId(), payment.getStatus());
        }, () -> log.warn("charge.dispute.closed for unknown charge {}", dispute.getCharge()));
    }

    /** transfer.created → record transferId on the matching payment. */
    private void handleTransferCreated(Event event) {
        Transfer transfer = (Transfer) event.getDataObjectDeserializer()
                .getObject().orElseThrow(() -> new IllegalStateException("No transfer in event " + event.getId()));

        // The transfer group format is "booking_<bookingId>" — we find via sourceTransaction (charge id)
        String sourceTransaction = transfer.getSourceTransaction();
        if (sourceTransaction != null) {
            paymentRepository.findByStripeChargeId(sourceTransaction).ifPresent(payment -> {
                if (payment.getStripeTransferId() == null) {
                    payment.setStripeTransferId(transfer.getId());
                    paymentRepository.save(payment);
                    log.info("Recorded transfer {} on payment {}", transfer.getId(), payment.getId());
                }
            });
        }
    }

    /** transfer.reversed → log; reconciliation handled by dispute resolution flow. */
    private void handleTransferReversed(Event event) {
        Transfer transfer = (Transfer) event.getDataObjectDeserializer()
                .getObject().orElseThrow(() -> new IllegalStateException("No transfer in event " + event.getId()));

        paymentRepository.findByStripeTransferId(transfer.getId()).ifPresent(payment -> {
            auditLogService.log(null, "TRANSFER_REVERSED", "Payment", payment.getId(),
                    "transferId=" + transfer.getId(), "transferReversed=true", null);
            log.info("Transfer {} reversed on payment {}", transfer.getId(), payment.getId());
        });
    }

    /** account.updated → sync MedicStripeAccount flags. */
    private void handleAccountUpdated(Event event) {
        Account account = (Account) event.getDataObjectDeserializer()
                .getObject().orElseThrow(() -> new IllegalStateException("No account in event " + event.getId()));

        String requirementsJson = null;
        if (account.getRequirements() != null
                && account.getRequirements().getCurrentlyDue() != null
                && !account.getRequirements().getCurrentlyDue().isEmpty()) {
            requirementsJson = account.getRequirements().getCurrentlyDue().toString();
        }

        stripeConnectService.syncAccountFromWebhook(
                account.getId(),
                Boolean.TRUE.equals(account.getChargesEnabled()),
                Boolean.TRUE.equals(account.getPayoutsEnabled()),
                Boolean.TRUE.equals(account.getDetailsSubmitted()),
                requirementsJson);
    }

    /** account.application.deauthorized → mark medic as offboarded. */
    private void handleAccountDeauthorized(Event event) {
        Account account = (Account) event.getDataObjectDeserializer()
                .getObject().orElseThrow(() -> new IllegalStateException("No account in event " + event.getId()));
        stripeConnectService.deauthorizeAccount(account.getId());
    }

    /** payout.paid → audit log for medic-facing payout history. */
    private void handlePayoutPaid(Event event) {
        Payout payout = (Payout) event.getDataObjectDeserializer()
                .getObject().orElseThrow(() -> new IllegalStateException("No payout in event " + event.getId()));
        log.info("Payout {} ({} bani) arrived for account {}",
                payout.getId(), payout.getAmount(), payout.getDestination());
    }

    /** payout.failed → audit log for medic-facing payout history. */
    private void handlePayoutFailed(Event event) {
        Payout payout = (Payout) event.getDataObjectDeserializer()
                .getObject().orElseThrow(() -> new IllegalStateException("No payout in event " + event.getId()));
        log.warn("Payout {} FAILED for account {}: {}", payout.getId(), payout.getDestination(),
                payout.getFailureMessage());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private void transitionTo(Payment payment, PaymentStatus expected, PaymentStatus to) {
        if (payment.getStatus() != expected) {
            log.warn("Expected payment {} in state {} but was {}; forcing transition to {}",
                    payment.getId(), expected, payment.getStatus(), to);
        }
        payment.setStatus(to);
    }
}
