package org.example.healthcare.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.healthcare.config.AiConfig;
import org.example.healthcare.dto.diagnosis.DiagnosisInferenceRequest;
import org.example.healthcare.dto.diagnosis.DiagnosisInferenceResponse;
import org.example.healthcare.service.AiDiagnosisService;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Anthropic-backed implementation of {@link AiDiagnosisService}.
 *
 * <p>Calls the Anthropic Messages API through a {@link WebClient} pre-configured
 * with the API key and base URL from {@link AiConfig}, mirroring the adapter
 * pattern used by {@code DailyVideoProvider}.
 *
 * <p>When {@code app.ai.enabled=false}, {@link #infer} returns a deterministic
 * mock suggestion (no network call) so the feature works without credentials.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiDiagnosisServiceImpl implements AiDiagnosisService {

    private static final String DISCLAIMER =
            "AI-generated suggestion for decision support only. " +
            "The treating physician is responsible for the final diagnosis.";

    private final AiConfig aiConfig;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public DiagnosisInferenceResponse infer(DiagnosisInferenceRequest request) {
        String medicationSummary = summarize(request.medications());

        if (!aiConfig.isEnabled()) {
            log.info("[AI STUB] infer skipped — app.ai.enabled=false. medications=[{}]", medicationSummary);
            return mockSuggestion(request.medications(), medicationSummary);
        }

        try {
            String text = callAnthropic(medicationSummary);
            JsonNode parsed = extractJson(text);
            return new DiagnosisInferenceResponse(
                    textOrDefault(parsed, "diagnosis", "Unable to determine"),
                    textOrDefault(parsed, "reasoning", ""),
                    normalizeConfidence(textOrDefault(parsed, "confidence", "low")),
                    DISCLAIMER,
                    false);
        } catch (WebClientResponseException e) {
            log.error("Anthropic infer failed: status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            // Degrade gracefully to the offline heuristic rather than failing the request.
            return mockSuggestion(request.medications(), medicationSummary);
        } catch (Exception e) {
            log.error("Anthropic infer failed: {}", e.getMessage(), e);
            return mockSuggestion(request.medications(), medicationSummary);
        }
    }

    // ── Anthropic call ─────────────────────────────────────────────────────────

    private String callAnthropic(String medicationSummary) throws Exception {
        String system = "You are a clinical decision-support assistant. Given a list of prescribed "
                + "medications, infer the single most likely diagnosis. Respond with ONLY a JSON object, "
                + "no prose, of the exact shape: "
                + "{\"diagnosis\": string, \"reasoning\": string (one sentence), "
                + "\"confidence\": \"low\"|\"medium\"|\"high\"}.";
        String userPrompt = "Prescribed medications:\n" + medicationSummary
                + "\n\nWhat is the most likely diagnosis?";

        Map<String, Object> body = new HashMap<>();
        body.put("model", aiConfig.getModel());
        body.put("max_tokens", aiConfig.getMaxTokens());
        body.put("system", system);
        body.put("messages", List.of(Map.of("role", "user", "content", userPrompt)));

        String raw = anthropicClient()
                .post()
                .uri("/messages")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode root = objectMapper.readTree(raw);
        JsonNode content = root.path("content");
        if (content.isArray() && content.size() > 0) {
            return content.get(0).path("text").asText("");
        }
        throw new IllegalStateException("Anthropic returned no content block");
    }

    private WebClient anthropicClient() {
        return webClientBuilder
                .baseUrl(aiConfig.getApiBase())
                .defaultHeader("x-api-key", aiConfig.getApiKey())
                .defaultHeader("anthropic-version", aiConfig.getApiVersion())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /** The model is asked for raw JSON; tolerate stray prose by slicing the outermost braces. */
    private JsonNode extractJson(String text) throws Exception {
        String trimmed = text == null ? "" : text.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            trimmed = trimmed.substring(start, end + 1);
        }
        return objectMapper.readTree(trimmed);
    }

    // ── Offline mock ────────────────────────────────────────────────────────────

    /**
     * Deterministic suggestion used when the AI gate is disabled or the remote
     * call fails. A small keyword heuristic keeps the stub plausible in demos.
     */
    private DiagnosisInferenceResponse mockSuggestion(
            List<DiagnosisInferenceRequest.Medication> meds, String summary) {
        String names = meds.stream()
                .map(m -> m.name() == null ? "" : m.name().toLowerCase())
                .collect(Collectors.joining(" "));

        String diagnosis;
        String reasoning;
        if (names.contains("paracetamol") || names.contains("nurofen")
                || names.contains("ibuprofen") || names.contains("acetaminophen")) {
            diagnosis = "Acute febrile / viral syndrome";
            reasoning = "Antipyretic and anti-inflammatory analgesics are typically prescribed for fever and pain of viral illness.";
        } else if (names.contains("amoxicillin") || names.contains("augmentin")
                || names.contains("azithromycin") || names.contains("penicillin")) {
            diagnosis = "Bacterial infection";
            reasoning = "The regimen centers on broad-spectrum antibiotics, indicating a bacterial infection.";
        } else if (names.contains("ventolin") || names.contains("salbutamol")
                || names.contains("budesonide")) {
            diagnosis = "Obstructive airway disease (asthma / bronchospasm)";
            reasoning = "Bronchodilator / inhaled corticosteroid therapy points to reactive airway disease.";
        } else if (names.contains("omeprazole") || names.contains("pantoprazole")
                || names.contains("esomeprazole")) {
            diagnosis = "Gastroesophageal reflux / acid-peptic disease";
            reasoning = "Proton-pump inhibitor therapy is characteristic of acid-related gastrointestinal conditions.";
        } else {
            diagnosis = "Indeterminate — clinical correlation required";
            reasoning = "The medication profile does not map to a single characteristic condition.";
        }

        return new DiagnosisInferenceResponse(
                diagnosis,
                reasoning,
                "low",
                DISCLAIMER + " (offline estimate — AI disabled)",
                true);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private static String summarize(List<DiagnosisInferenceRequest.Medication> meds) {
        return meds.stream().map(m -> {
            StringBuilder sb = new StringBuilder(m.name() == null ? "" : m.name());
            if (m.dosage() != null && !m.dosage().isBlank()) {
                sb.append(' ').append(m.dosage());
                if (m.unit() != null && !m.unit().isBlank()) sb.append(m.unit());
            }
            if (m.frequency() != null && !m.frequency().isBlank()) sb.append(", ").append(m.frequency());
            if (m.durationDays() != null && m.durationDays() > 0) sb.append(", ").append(m.durationDays()).append(" days");
            return sb.toString().trim();
        }).collect(Collectors.joining("; "));
    }

    private static String textOrDefault(JsonNode node, String field, String fallback) {
        JsonNode v = node.get(field);
        return v != null && !v.isNull() && !v.asText().isBlank() ? v.asText() : fallback;
    }

    private static String normalizeConfidence(String raw) {
        String c = raw == null ? "" : raw.toLowerCase().trim();
        return (c.equals("low") || c.equals("medium") || c.equals("high")) ? c : "low";
    }
}
