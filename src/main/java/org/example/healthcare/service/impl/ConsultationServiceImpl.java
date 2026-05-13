package org.example.healthcare.service.impl;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.healthcare.common.EncryptionService;
import org.example.healthcare.common.StripePaymentService;
import org.example.healthcare.common.exception.BusinessException;
import org.example.healthcare.common.exception.ResourceNotFoundException;
import org.example.healthcare.dto.consultation.*;
import org.example.healthcare.event.BookingConfirmedEvent;
import org.example.healthcare.model.*;
import org.example.healthcare.repository.*;
import org.example.healthcare.service.AuditLogService;
import org.example.healthcare.service.ConsultationService;
import org.example.healthcare.video.DailyVideoProvider;
import org.example.healthcare.video.ParticipantRole;
import org.example.healthcare.video.RoomPrivacy;
import org.example.healthcare.video.VideoConfig;
import org.example.healthcare.video.VideoMeetingToken;
import org.example.healthcare.video.VideoProvider;
import org.example.healthcare.video.VideoRoom;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsultationServiceImpl implements ConsultationService {

    /** Dispute window: payment is released 48 h after consultation ends. */
    private static final long RELEASE_WINDOW_HOURS = 48;

    private final ConsultationRepository    consultationRepository;
    private final BookingRepository         bookingRepository;
    private final PaymentRepository         paymentRepository;
    private final UserRepository            userRepository;
    private final VideoSessionEventRepository videoSessionEventRepository;
    private final MeetingTokenRepository    meetingTokenRepository;
    private final EncryptionService         encryptionService;
    private final StripePaymentService      stripePaymentService;
    private final VideoProvider             videoProvider;
    private final VideoConfig               videoConfig;
    private final AuditLogService           auditLogService;
    private final ObjectMapper              objectMapper;

    // ── BookingConfirmedEvent listener ────────────────────────────────────────

    /**
     * Creates the Consultation record in SCHEDULED status and provisions a video room
     * as soon as a booking is confirmed. Idempotent — if the consultation already exists
     * the method returns early without creating a duplicate.
     *
     * <p>Called via Spring ApplicationEvent from BookingServiceImpl.create().
     */
    @Override
    @Transactional
    @EventListener
    public ConsultationResponse createForBooking(UUID bookingId) {
        // Idempotency guard — safe on event replay
        return consultationRepository.findByBookingId(bookingId)
                .map(existing -> {
                    log.debug("createForBooking: consultation already exists for bookingId={}", bookingId);
                    return toResponse(existing, null);
                })
                .orElseGet(() -> {
                    Booking booking = bookingRepository.findById(bookingId)
                            .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
                    return createConsultationWithRoom(booking);
                });
    }

    /** Handles the Spring ApplicationEvent published by BookingServiceImpl. */
    @Transactional
    @org.springframework.context.event.EventListener
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        createForBooking(event.getBookingId());
    }

    private ConsultationResponse createConsultationWithRoom(Booking booking) {
        // Calculate room expiry = slot end + max-room-ttl buffer
        Instant slotEnd    = booking.getSlot().getEndsAt();
        Instant roomExpiry = slotEnd.plus(videoConfig.getMaxRoomTtl());
        Instant earliest   = booking.getSlot().getStartsAt()
                .minus(5, ChronoUnit.MINUTES); // allow early join 5 min before

        VideoRoom room = videoProvider.createRoom(
                null, // we use bookingId as seed below; pass null to use stable name
                earliest, roomExpiry, RoomPrivacy.PRIVATE);

        // Override with booking-stable room name when provider returns a generated one
        // DailyVideoProvider.roomName uses the consultationId but we don't have it yet;
        // use bookingId as a stable seed before the UUID is generated.
        String stableRoomName = "booking-" + booking.getId();
        VideoRoom roomForBooking = new VideoRoom(
                room.providerName(), stableRoomName, room.roomUrl(), room.expiresAt());

        Consultation consultation = Consultation.builder()
                .booking(booking)
                .status(ConsultationStatus.SCHEDULED)
                .videoRoomId(stableRoomName)
                .videoRoomUrl(roomForBooking.roomUrl())
                .videoProvider(roomForBooking.providerName())
                .videoRoomExpiresAt(roomForBooking.expiresAt())
                .build();
        consultation = consultationRepository.save(consultation);

        auditLogService.log(null, "VIDEO_ROOM_CREATED", "Consultation", consultation.getId(),
                null, "roomName=" + stableRoomName, null);

        log.info("Consultation created in SCHEDULED: id={} bookingId={} roomUrl={}",
                consultation.getId(), booking.getId(), roomForBooking.roomUrl());
        return toResponse(consultation, null);
    }

    // ── Lifecycle methods ─────────────────────────────────────────────────────

    @Override
    @Transactional
    public ConsultationResponse start(UUID bookingId, UUID medicUserId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));

        if (!booking.getMedic().getUser().getId().equals(medicUserId))
            throw new BusinessException("Only the booked medic can start this consultation");

        // Consultation must already exist in SCHEDULED state (created by createForBooking)
        Consultation consultation = consultationRepository.findByBookingId(bookingId)
                .orElseGet(() -> {
                    // Fallback: create the consultation on-the-fly if the event was missed
                    log.warn("Consultation not pre-created for bookingId={}; creating now in start()", bookingId);
                    return consultationRepository.save(Consultation.builder()
                            .booking(booking)
                            .status(ConsultationStatus.SCHEDULED)
                            .videoRoomId("room_" + bookingId)
                            .videoProvider("daily")
                            .build());
                });

        if (consultation.getStatus() != ConsultationStatus.SCHEDULED)
            throw new BusinessException("Consultation is not in SCHEDULED state (current: " + consultation.getStatus() + ")");

        consultation.setStatus(ConsultationStatus.IN_PROGRESS);
        consultation.setStartedAt(Instant.now());

        // Capture the Stripe escrow now that the medic is present
        Payment payment = paymentRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new BusinessException("No payment record for booking " + bookingId));
        stripePaymentService.capturePaymentIntent(payment.getStripePaymentIntentId());
        payment.setStatus(PaymentStatus.HELD);

        log.info("Consultation started: id={} bookingId={}", consultation.getId(), bookingId);
        return toResponse(consultation, null);
    }

    @Override
    @Transactional
    public ConsultationResponse complete(UUID consultationId, UUID medicUserId, int durationSeconds) {
        Consultation consultation = findOrThrow(consultationId);
        assertMedicOwns(consultation, medicUserId);

        if (consultation.getStatus() != ConsultationStatus.IN_PROGRESS)
            throw new BusinessException("Consultation is not IN_PROGRESS");

        Instant now = Instant.now();
        consultation.setStatus(ConsultationStatus.COMPLETED);
        consultation.setEndedAt(now);
        consultation.setDurationSeconds(durationSeconds);
        consultation.setReleaseAt(now.plus(RELEASE_WINDOW_HOURS, ChronoUnit.HOURS));

        return toResponse(consultation, null);
    }

    @Override
    @Transactional
    public ConsultationResponse markFailed(UUID consultationId, ConsultationFailureReason reason) {
        Consultation consultation = findOrThrow(consultationId);

        if (consultation.getStatus() == ConsultationStatus.COMPLETED)
            throw new BusinessException("Cannot fail a completed consultation");

        consultation.setStatus(ConsultationStatus.FAILED);
        consultation.setEndedAt(Instant.now());
        consultation.setFailureReason(reason != null ? reason : ConsultationFailureReason.OTHER);

        // Delete the video room to immediately disconnect any lingering participants
        if (consultation.getVideoRoomId() != null) {
            try {
                videoProvider.deleteRoom(consultation.getVideoRoomId());
            } catch (Exception e) {
                log.warn("Failed to delete video room {} on markFailed: {}", consultation.getVideoRoomId(), e.getMessage());
            }
        }

        // Trigger automatic refund for failure types that warrant it
        boolean shouldRefund = reason == ConsultationFailureReason.MEDIC_NO_SHOW
                || reason == ConsultationFailureReason.TECHNICAL_FAILURE
                || reason == null;

        if (shouldRefund) {
            paymentRepository.findByBookingId(consultation.getBooking().getId()).ifPresent(payment -> {
                stripePaymentService.refund(payment.getStripePaymentIntentId(), null);
                payment.setStatus(PaymentStatus.REFUNDED);
                payment.setRefundedAt(Instant.now());
            });
        }

        auditLogService.log(null, "CONSULTATION_FAILED", "Consultation", consultationId,
                "status=IN_PROGRESS", "status=FAILED,reason=" + consultation.getFailureReason(), null);

        return toResponse(consultation, null);
    }

    @Override
    @Transactional
    public ConsultationResponse markFailed(UUID consultationId) {
        return markFailed(consultationId, ConsultationFailureReason.OTHER);
    }

    // ── Video token endpoints ─────────────────────────────────────────────────

    @Override
    @Transactional
    public JoinTokenResponse joinToken(UUID consultationId, UUID principalUserId) {
        Consultation consultation = findOrThrow(consultationId);

        // Validate principal is either the booked patient or the medic
        boolean isPatient = consultation.getBooking().getPatient().getId().equals(principalUserId);
        boolean isMedic   = consultation.getBooking().getMedic().getUser().getId().equals(principalUserId);
        if (!isPatient && !isMedic)
            throw new BusinessException("Access denied — not a party to this consultation");

        if (consultation.getStatus() == ConsultationStatus.COMPLETED
                || consultation.getStatus() == ConsultationStatus.FAILED)
            throw new BusinessException("Cannot join a " + consultation.getStatus() + " consultation");

        ParticipantRole role = isMedic ? ParticipantRole.OWNER : ParticipantRole.PARTICIPANT;
        User user = userRepository.findById(principalUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", principalUserId));

        String displayName = user.getEmail(); // fallback display name

        VideoMeetingToken token = videoProvider.issueToken(
                consultationId,
                consultation.getVideoRoomId(),
                principalUserId,
                displayName,
                role,
                videoConfig.getTokenTtl());

        // Persist the token record for audit + revocation
        MeetingTokenRecord record = MeetingTokenRecord.builder()
                .consultation(consultation)
                .user(user)
                .role(role.name())
                .tokenJti(token.jti())
                .issuedAt(Instant.now())
                .expiresAt(token.expiresAt())
                .build();
        meetingTokenRepository.save(record);

        auditLogService.log(principalUserId, "VIDEO_TOKEN_ISSUED", "Consultation", consultationId,
                null, "role=" + role + ",jti=" + token.jti(), null);

        log.info("Meeting token issued: consultationId={} userId={} role={} jti={}",
                consultationId, principalUserId, role, token.jti());

        String roomUrl = consultation.getVideoRoomUrl() != null
                ? consultation.getVideoRoomUrl()
                : "https://" + videoConfig.getDaily().getDomain() + "/" + consultation.getVideoRoomId();

        return new JoinTokenResponse(roomUrl, token.token(), role.name(), token.expiresAt());
    }

    @Override
    @Transactional
    public void recordHeartbeat(UUID consultationId, UUID principalUserId, HeartbeatRequest request) {
        Consultation consultation = findOrThrow(consultationId);

        boolean isParty = consultation.getBooking().getPatient().getId().equals(principalUserId)
                || consultation.getBooking().getMedic().getUser().getId().equals(principalUserId);
        if (!isParty)
            throw new BusinessException("Access denied");

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(
                    Map.of("clientUtcTimestamp", request.clientUtcTimestamp().toString(),
                            "networkRttMs", String.valueOf(request.networkRttMs()),
                            "mediaState",    request.mediaState() != null ? request.mediaState() : "unknown",
                            "userId",        principalUserId.toString()));
        } catch (JacksonException e) {
            payloadJson = "{\"error\":\"serialization_failed\"}";
        }

        VideoSessionEvent event = VideoSessionEvent.builder()
                .consultation(consultation)
                .eventType("heartbeat")
                .actorUserId(principalUserId)
                .payload(payloadJson)
                .occurredAt(request.clientUtcTimestamp())
                .build();
        videoSessionEventRepository.save(event);
    }

    @Override
    public DiagnosticsResponse getDiagnostics(UUID consultationId, UUID principalUserId) {
        Consultation consultation = findOrThrow(consultationId);

        boolean isMedic = consultation.getBooking().getMedic().getUser().getId().equals(principalUserId);
        if (!isMedic)
            throw new BusinessException("Diagnostics are only available to the medic or admin");

        List<VideoSessionEvent> events =
                videoSessionEventRepository.findByConsultationIdOrderByOccurredAtAsc(consultationId);

        long heartbeatCount = events.stream().filter(e -> "heartbeat".equals(e.getEventType())).count();

        List<DiagnosticsResponse.EventSummary> summaries = events.stream()
                .map(e -> new DiagnosticsResponse.EventSummary(
                        e.getEventType(), e.getActorUserId(), e.getOccurredAt(), e.getPayload()))
                .toList();

        return new DiagnosticsResponse(
                consultationId,
                summaries,
                consultation.getFirstJoinedAt(),
                consultation.getLastLeftAt(),
                heartbeatCount);
    }

    // ── Read methods ──────────────────────────────────────────────────────────

    @Override
    public ConsultationResponse getByBookingId(UUID bookingId, UUID principalId) {
        Consultation consultation = consultationRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation", bookingId));
        return getById(consultation.getId(), principalId);
    }

    @Override
    public ConsultationResponse getById(UUID id, UUID principalId) {
        Consultation consultation = findOrThrow(id);
        boolean isPatient = consultation.getBooking().getPatient().getId().equals(principalId);
        boolean isMedic   = consultation.getBooking().getMedic().getUser().getId().equals(principalId);
        if (!isPatient && !isMedic)
            throw new BusinessException("Access denied");

        String notes = null;
        if (isMedic && consultation.getNotesEncrypted() != null)
            notes = encryptionService.decrypt(consultation.getNotesEncrypted());

        return toResponse(consultation, notes);
    }

    @Override
    public Page<ConsultationResponse> getPatientHistory(UUID patientId, Pageable pageable) {
        return consultationRepository.findByPatientId(patientId, pageable)
                .map(c -> toResponse(c, null));
    }

    @Override
    public Page<ConsultationResponse> getMedicHistory(UUID medicId, Pageable pageable) {
        return consultationRepository.findByMedicId(medicId, pageable)
                .map(c -> toResponse(c, null));
    }

    @Override
    @Transactional(readOnly = true)
    public ConsultationNoteResponse getNotes(UUID consultationId, UUID principalId) {
        Consultation consultation = findOrThrow(consultationId);
        String decrypted = consultation.getNotesEncrypted() != null
                ? encryptionService.decrypt(consultation.getNotesEncrypted())
                : "";
        return new ConsultationNoteResponse(
                consultation.getId(),
                consultation.getId(),
                decrypted,
                null,
                consultation.getEndedAt() != null ? consultation.getEndedAt() : consultation.getStartedAt(),
                false
        );
    }

    @Override
    @Transactional
    public ConsultationResponse saveNotes(UUID consultationId, UUID medicUserId, ConsultationNotesRequest request) {
        Consultation consultation = findOrThrow(consultationId);
        assertMedicOwns(consultation, medicUserId);

        if (consultation.getStatus() != ConsultationStatus.COMPLETED
                && consultation.getStatus() != ConsultationStatus.IN_PROGRESS)
            throw new BusinessException("Notes can only be saved during an active or completed consultation");

        consultation.setNotesEncrypted(encryptionService.encrypt(request.notes()));
        return toResponse(consultation, request.notes());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void assertMedicOwns(Consultation consultation, UUID medicUserId) {
        if (!consultation.getBooking().getMedic().getUser().getId().equals(medicUserId))
            throw new BusinessException("Access denied");
    }

    private Consultation findOrThrow(UUID id) {
        return consultationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation", id));
    }

    private ConsultationResponse toResponse(Consultation c, String decryptedNotes) {
        return new ConsultationResponse(
                c.getId(),
                c.getBooking().getId(),
                c.getStatus(),
                c.getVideoRoomId(),
                c.getVideoRoomUrl(),
                c.getVideoProvider(),
                c.getStartedAt(),
                c.getEndedAt(),
                c.getDurationSeconds(),
                decryptedNotes,
                c.getReleaseAt(),
                c.getFailureReason());
    }
}
