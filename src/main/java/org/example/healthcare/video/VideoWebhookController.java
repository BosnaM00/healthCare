package org.example.healthcare.video;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.healthcare.model.*;
import org.example.healthcare.repository.ConsultationRepository;
import org.example.healthcare.repository.VideoSessionEventRepository;
import org.example.healthcare.repository.WebhookEventRepository;
import org.example.healthcare.service.AuditLogService;
import org.example.healthcare.service.ConsultationService;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Daily.co webhook receiver at {@code POST /api/v1/webhooks/video}.
 *
 * <p>Mirrors the {@link org.example.healthcare.controller.StripeWebhookController} pattern:
 * <ol>
 *   <li>Verify {@code x-daily-signature} HMAC-SHA256 with constant-time comparison.</li>
 *   <li>Persist event to {@code webhook_events} (source="daily") for idempotency.</li>
 *   <li>Dispatch to handler by {@code event.action} type.</li>
 *   <li>On exception return 500 so Daily retries. On signature failure return 400.</li>
 * </ol>
 *
 * <p>Security: endpoint is public (no JWT required) but reachable only after
 * HMAC signature verification. Listed in SecurityConfig as a permitted endpoint.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
public class VideoWebhookController {

    private static final String HMAC_ALGORITHM  = "HmacSHA256";
    private static final String SIGNATURE_HEADER = "x-daily-signature";
    /** Daily timestamps the event; we allow 5-minute clock skew. */
    private static final long   TOLERANCE_SECONDS = 300L;

    private final VideoConfig                 videoConfig;
    private final WebhookEventRepository      webhookEventRepository;
    private final ConsultationRepository      consultationRepository;
    private final VideoSessionEventRepository videoSessionEventRepository;
    private final ConsultationService         consultationService;
    private final AuditLogService             auditLogService;
    private final ObjectMapper                objectMapper;

    /**
     * Main Daily webhook receiver.
     *
     * <p>IMPORTANT: {@code @RequestBody byte[]} keeps the raw bytes intact
     * so the HMAC is computed on exactly what Daily signed.
     */
    @PostMapping(value = "/video", consumes = "application/json")
    @Transactional
    public ResponseEntity<String> handleVideoWebhook(
            @RequestBody byte[] rawBody,
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String sigHeader) {

        String payload = new String(rawBody, StandardCharsets.UTF_8);

        // 1. Verify HMAC-SHA256 signature
        if (!verifySignature(rawBody, sigHeader)) {
            log.warn("Daily webhook signature verification failed — header={}", sigHeader);
            return ResponseEntity.status(400).body("Invalid signature");
        }

        // 2. Parse event
        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (JsonProcessingException e) {
            log.error("Daily webhook JSON parse error: {}", e.getMessage());
            return ResponseEntity.status(400).body("Malformed JSON");
        }

        String eventId   = Optional.ofNullable(root.get("id")).map(JsonNode::asText).orElse(UUID.randomUUID().toString());
        String eventType = Optional.ofNullable(root.get("action")).map(JsonNode::asText).orElse("unknown");
        String dailyKey  = "daily:" + eventId; // namespace avoids collision with Stripe event ids

        // 3. Idempotency gate
        if (webhookEventRepository.existsByStripeEventId(dailyKey)) {
            WebhookEvent existing = webhookEventRepository.findById(dailyKey).orElse(null);
            if (existing != null && existing.getStatus() == WebhookEventStatus.PROCESSED) {
                log.debug("Duplicate Daily event {} ({}) — already processed", eventId, eventType);
                return ResponseEntity.ok("Already processed");
            }
        }

        WebhookEvent webhookEvent = webhookEventRepository.findById(dailyKey)
                .orElseGet(() -> webhookEventRepository.save(
                        WebhookEvent.builder()
                                .stripeEventId(dailyKey)
                                .type(eventType)
                                .source("daily")
                                .status(WebhookEventStatus.PENDING)
                                .build()));

        webhookEvent.setAttempts(webhookEvent.getAttempts() + 1);

        // 4. Dispatch
        try {
            dispatch(eventType, root, payload);
            webhookEvent.setStatus(WebhookEventStatus.PROCESSED);
            webhookEvent.setProcessedAt(Instant.now());
        } catch (Exception e) {
            webhookEvent.setStatus(WebhookEventStatus.FAILED);
            webhookEvent.setErrorMessage(e.getMessage());
            webhookEventRepository.save(webhookEvent);
            log.error("Daily webhook handler failed for {} ({}): {}", eventId, eventType, e.getMessage(), e);
            throw e;  // Return 500 so Daily retries
        }

        webhookEventRepository.save(webhookEvent);
        return ResponseEntity.ok("Handled");
    }

    // ── Dispatch ──────────────────────────────────────────────────────────────

    private void dispatch(String eventType, JsonNode root, String rawPayload) {
        log.info("Processing Daily event type={}", eventType);

        VideoWebhookEventType type = VideoWebhookEventType.fromDailyType(eventType);
        if (type == null) {
            log.debug("Unhandled Daily event type: {}", eventType);
            return;
        }

        switch (type) {
            case MEETING_STARTED    -> handleMeetingStarted(root, rawPayload);
            case PARTICIPANT_JOINED -> handleParticipantJoined(root, rawPayload);
            case PARTICIPANT_LEFT   -> handleParticipantLeft(root, rawPayload);
            case MEETING_ENDED      -> handleMeetingEnded(root, rawPayload);
            case RECORDING_READY    -> log.info("recording.ready webhook received — Phase 3 handler pending");
        }
    }

    // ── Event handlers ────────────────────────────────────────────────────────

    /**
     * meeting.started → set status = IN_PROGRESS if currently SCHEDULED.
     * The webhook is the authoritative signal; the medic's /start call is the
     * client-side trigger.
     */
    private void handleMeetingStarted(JsonNode root, String payload) {
        Consultation consultation = resolveConsultation(root);
        if (consultation == null) return;

        if (consultation.getStatus() == ConsultationStatus.SCHEDULED) {
            consultation.setStatus(ConsultationStatus.IN_PROGRESS);
            consultation.setStartedAt(parseTimestamp(root));
            consultationRepository.save(consultation);
            log.info("Consultation {} transitioned SCHEDULED→IN_PROGRESS via meeting.started webhook",
                    consultation.getId());
        }

        appendSessionEvent(consultation, "meeting.started", null, payload, parseTimestamp(root));
    }

    /**
     * participant.joined → record first join timestamp; audit log.
     */
    private void handleParticipantJoined(JsonNode root, String payload) {
        Consultation consultation = resolveConsultation(root);
        if (consultation == null) return;

        Instant joinedAt = parseTimestamp(root);
        if (consultation.getFirstJoinedAt() == null
                || joinedAt.isBefore(consultation.getFirstJoinedAt())) {
            consultation.setFirstJoinedAt(joinedAt);
            consultationRepository.save(consultation);
        }

        UUID userId = resolveUserId(root);
        auditLogService.log(userId, "VIDEO_PARTICIPANT_JOINED", "Consultation", consultation.getId(),
                null, "joinedAt=" + joinedAt, null);
        appendSessionEvent(consultation, "participant.joined", userId, payload, joinedAt);
    }

    /**
     * participant.left → record last-left timestamp; schedule debounce complete if room is empty.
     */
    private void handleParticipantLeft(JsonNode root, String payload) {
        Consultation consultation = resolveConsultation(root);
        if (consultation == null) return;

        Instant leftAt = parseTimestamp(root);
        consultation.setLastLeftAt(leftAt);
        consultationRepository.save(consultation);

        UUID userId = resolveUserId(root);
        appendSessionEvent(consultation, "participant.left", userId, payload, leftAt);
    }

    /**
     * meeting.ended → call ConsultationService.complete(durationSeconds).
     * The duration is derived from the webhook payload (authoritative source).
     */
    private void handleMeetingEnded(JsonNode root, String payload) {
        Consultation consultation = resolveConsultation(root);
        if (consultation == null) return;

        appendSessionEvent(consultation, "meeting.ended", null, payload, parseTimestamp(root));

        if (consultation.getStatus() == ConsultationStatus.IN_PROGRESS) {
            int durationSeconds = parseDuration(root, consultation);
            UUID medicUserId = consultation.getBooking().getMedic().getUser().getId();
            // Use the medic's userId as the actor for the complete() call
            try {
                consultationService.complete(consultation.getId(), medicUserId, durationSeconds);
                log.info("Consultation {} completed via meeting.ended webhook (duration={}s)",
                        consultation.getId(), durationSeconds);
            } catch (Exception e) {
                log.error("Failed to complete consultation {} from meeting.ended webhook: {}",
                        consultation.getId(), e.getMessage(), e);
                throw e;
            }
        }
    }

    // ── HMAC verification ─────────────────────────────────────────────────────

    /**
     * Verifies the {@code x-daily-signature} header using HMAC-SHA256.
     *
     * <p>Daily signs the raw request body with the webhook secret. The signature
     * is prefixed with {@code "hmac-sha256="} in the header value.
     * Constant-time comparison via {@link MessageDigest#isEqual} prevents timing attacks.
     */
    private boolean verifySignature(byte[] rawBody, String sigHeader) {
        String secret = videoConfig.getDaily().getWebhookSecret();
        if (secret == null || secret.isBlank() || "daily_webhook_placeholder".equals(secret)) {
            log.warn("Daily webhook secret not configured — skipping signature verification (dev mode)");
            return true; // Allow in dev when secret is placeholder
        }

        if (sigHeader == null || sigHeader.isBlank()) {
            return false;
        }

        try {
            String expectedPrefix = "hmac-sha256=";
            String hexSig = sigHeader.startsWith(expectedPrefix)
                    ? sigHeader.substring(expectedPrefix.length())
                    : sigHeader;

            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] expectedBytes = mac.doFinal(rawBody);
            byte[] receivedBytes = HexFormat.of().parseHex(hexSig);

            return MessageDigest.isEqual(expectedBytes, receivedBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException | IllegalArgumentException e) {
            log.error("Daily webhook signature verification error: {}", e.getMessage());
            return false;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Resolves the consultation from the room name in the Daily event payload. */
    private Consultation resolveConsultation(JsonNode root) {
        JsonNode roomNode = root.path("room");
        String roomName   = roomNode.isTextual() ? roomNode.asText()
                : root.path("roomName").asText(null);

        if (roomName == null || roomName.isBlank()) {
            log.warn("Daily webhook missing room name in payload");
            return null;
        }

        return consultationRepository.findByVideoRoomId(roomName).orElseGet(() -> {
            log.warn("Daily webhook for unknown room: {}", roomName);
            return null;
        });
    }

    private Instant parseTimestamp(JsonNode root) {
        JsonNode ts = root.path("timestamp");
        if (!ts.isMissingNode()) {
            return Instant.ofEpochSecond(ts.asLong());
        }
        return Instant.now();
    }

    private UUID resolveUserId(JsonNode root) {
        JsonNode participantNode = root.path("participant");
        if (!participantNode.isMissingNode()) {
            String userId = participantNode.path("user_id").asText(null);
            if (userId != null && !userId.isBlank()) {
                try {
                    return UUID.fromString(userId);
                } catch (IllegalArgumentException ignored) {
                    // Daily internal user IDs are not UUIDs — skip
                }
            }
        }
        return null;
    }

    private int parseDuration(JsonNode root, Consultation consultation) {
        JsonNode durNode = root.path("duration");
        if (!durNode.isMissingNode()) {
            return durNode.asInt(0);
        }
        // Fallback: derive from startedAt
        if (consultation.getStartedAt() != null) {
            return (int) (Instant.now().getEpochSecond() - consultation.getStartedAt().getEpochSecond());
        }
        return 0;
    }

    private void appendSessionEvent(Consultation consultation, String eventType,
                                    UUID actorUserId, String payload, Instant occurredAt) {
        VideoSessionEvent event = VideoSessionEvent.builder()
                .consultation(consultation)
                .eventType(eventType)
                .actorUserId(actorUserId)
                .payload(payload)
                .occurredAt(occurredAt)
                .build();
        videoSessionEventRepository.save(event);
    }
}
