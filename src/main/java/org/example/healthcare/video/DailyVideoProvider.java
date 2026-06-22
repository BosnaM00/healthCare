package org.example.healthcare.video;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Daily.co implementation of {@link VideoProvider}.
 *
 * <p>All Daily REST calls are made via a {@link WebClient} pre-configured with the
 * API key and base URL from {@link VideoConfig}. The adapter is the only place in
 * the codebase that knows about Daily-specific request/response shapes.
 *
 * <p>When {@code app.video.enabled=false}, both {@link #createRoom} and
 * {@link #issueToken} return deterministic stub values so the rest of the booking
 * flow works in CI without real Daily credentials.
 *
 * <p>Room naming convention: {@code consultation-<consultationId>} — unique per
 * consultation and safe to replay on webhook re-delivery.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyVideoProvider implements VideoProvider {

    private static final String PROVIDER_NAME = "daily";

    private final VideoConfig videoConfig;
    private final WebClient.Builder webClientBuilder;

    // ── VideoProvider interface ───────────────────────────────────────────────

    @Override
    public VideoRoom createRoom(UUID consultationId,
                                Instant earliestJoinAt,
                                Instant expiresAt,
                                RoomPrivacy privacy) {

        String roomName = roomName(consultationId);

        if (!videoConfig.isEnabled()) {
            log.info("[VIDEO STUB] createRoom skipped — app.video.enabled=false. roomName={}", roomName);
            String stubUrl = "https://" + videoConfig.getDaily().getDomain() + "/" + roomName;
            return new VideoRoom(PROVIDER_NAME, roomName, stubUrl, expiresAt);
        }

        // Build Daily room creation request
        Map<String, Object> properties = new HashMap<>();
        properties.put("exp",          expiresAt.getEpochSecond());
        properties.put("nbf",          earliestJoinAt.getEpochSecond());
        properties.put("privacy",      privacy == RoomPrivacy.PRIVATE ? "private" : "public");
        properties.put("enable_recording", "off");   // GDPR: recording off by default (§9.2)
        properties.put("geo",          videoConfig.getDaily().getRegion()); // EU data residency

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("name",       roomName);
        requestBody.put("properties", properties);

        try {
            DailyRoomResponse response = dailyClient()
                    .post()
                    .uri("/rooms")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(DailyRoomResponse.class)
                    .block();

            if (response == null) {
                throw new VideoProviderException("Daily.co returned null response for createRoom");
            }

            log.info("Video room created: roomName={} url={}", response.name, response.url);
            return new VideoRoom(PROVIDER_NAME, response.name, response.url,
                    Instant.ofEpochSecond(response.config != null ? response.config.exp : expiresAt.getEpochSecond()));

        } catch (WebClientResponseException e) {
            // 409 Conflict → room already exists; fetch and return existing
            if (e.getStatusCode().value() == 409) {
                log.info("Room {} already exists on Daily.co — fetching existing room", roomName);
                return getExistingRoom(roomName, expiresAt);
            }
            log.error("Daily.co createRoom failed: status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new VideoProviderException("Failed to create Daily.co room: " + e.getMessage(), e);
        }
    }

    @Override
    public VideoMeetingToken issueToken(UUID consultationId,
                                        String roomName,
                                        UUID userId,
                                        String displayName,
                                        ParticipantRole role,
                                        Duration ttl) {

        String jti = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(ttl);

        if (!videoConfig.isEnabled()) {
            log.info("[VIDEO STUB] issueToken skipped — app.video.enabled=false. roomName={} role={}", roomName, role);
            return new VideoMeetingToken("stub-token-" + jti, jti, role, expiresAt);
        }

        Map<String, Object> properties = new HashMap<>();
        properties.put("room_name",    roomName);
        properties.put("user_id",      userId.toString());
        properties.put("user_name",    displayName);
        properties.put("exp",          expiresAt.getEpochSecond());
        properties.put("is_owner",     role == ParticipantRole.OWNER);
        // Disable recording for non-owner participants; recording controlled by OWNER
        properties.put("start_cloud_recording", false);

        // Daily's /meeting-tokens API expects the token claims nested under "properties"
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("properties", properties);

        try {
            DailyTokenResponse response = dailyClient()
                    .post()
                    .uri("/meeting-tokens")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(DailyTokenResponse.class)
                    .block();

            if (response == null || response.token == null) {
                throw new VideoProviderException("Daily.co returned null token for issueToken");
            }

            log.info("Meeting token issued: consultationId={} userId={} role={}", consultationId, userId, role);
            // Daily tokens do not expose the JTI; we generate our own for revocation tracking
            return new VideoMeetingToken(response.token, jti, role, expiresAt);

        } catch (WebClientResponseException e) {
            log.error("Daily.co issueToken failed: status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new VideoProviderException("Failed to issue Daily.co token: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteRoom(String roomName) {
        if (!videoConfig.isEnabled()) {
            log.info("[VIDEO STUB] deleteRoom skipped — app.video.enabled=false. roomName={}", roomName);
            return;
        }

        try {
            dailyClient()
                    .delete()
                    .uri("/rooms/{name}", roomName)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            log.info("Video room deleted: roomName={}", roomName);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                log.warn("deleteRoom: room {} not found on Daily.co — treating as already deleted", roomName);
                return;
            }
            log.error("Daily.co deleteRoom failed: status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new VideoProviderException("Failed to delete Daily.co room: " + e.getMessage(), e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Stable room name derived from the consultation UUID. */
    public static String roomName(UUID consultationId) {
        return "consultation-" + consultationId.toString();
    }

    private VideoRoom getExistingRoom(String roomName, Instant fallbackExpiry) {
        try {
            DailyRoomResponse response = dailyClient()
                    .get()
                    .uri("/rooms/{name}", roomName)
                    .retrieve()
                    .bodyToMono(DailyRoomResponse.class)
                    .block();

            if (response == null) {
                throw new VideoProviderException("Daily.co returned null response for getRoom(" + roomName + ")");
            }

            Instant exp = (response.config != null && response.config.exp > 0)
                    ? Instant.ofEpochSecond(response.config.exp)
                    : fallbackExpiry;
            return new VideoRoom(PROVIDER_NAME, response.name, response.url, exp);
        } catch (WebClientResponseException e) {
            throw new VideoProviderException("Failed to fetch existing Daily.co room: " + e.getMessage(), e);
        }
    }

    private WebClient dailyClient() {
        return webClientBuilder
                .baseUrl(videoConfig.getDaily().getApiBase())
                .defaultHeader("Authorization", "Bearer " + videoConfig.getDaily().getApiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    // ── Daily REST response shapes ────────────────────────────────────────────

    private static class DailyRoomResponse {
        public String name;
        public String url;
        public RoomConfig config;

        static class RoomConfig {
            public long exp;
            public long nbf;
        }
    }

    private static class DailyTokenResponse {
        public String token;
    }
}
