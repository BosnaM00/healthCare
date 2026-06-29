package org.example.healthcare.service.impl;

import org.example.healthcare.config.AiConfig;
import org.example.healthcare.dto.diagnosis.DiagnosisInferenceRequest;
import org.example.healthcare.dto.diagnosis.DiagnosisInferenceResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the deterministic offline path of {@link AiDiagnosisServiceImpl}.
 *
 * <p>With {@code app.ai.enabled=false} (the default), {@code infer} never makes a
 * network call — it returns a keyword-based mock suggestion. The {@link AiConfig}
 * is left at its defaults and the {@code WebClient.Builder} is unused on this path,
 * so the service can be constructed with a {@code null} builder.
 */
@DisplayName("AiDiagnosisServiceImpl (offline mock path)")
class AiDiagnosisServiceImplTest {

    private final AiDiagnosisServiceImpl service =
            new AiDiagnosisServiceImpl(new AiConfig(), null);

    private static DiagnosisInferenceRequest requestFor(String medicationName) {
        var med = new DiagnosisInferenceRequest.Medication(
                medicationName, "500", "mg", "twice daily", 5);
        return new DiagnosisInferenceRequest(List.of(med));
    }

    @Test
    @DisplayName("flags the suggestion as a mock when AI is disabled")
    void marksResponseAsMock() {
        DiagnosisInferenceResponse response = service.infer(requestFor("Paracetamol"));

        assertThat(response.mock()).isTrue();
        assertThat(response.confidence()).isEqualTo("low");
        assertThat(response.disclaimer()).contains("offline");
    }

    @Test
    @DisplayName("maps an antipyretic to a febrile/viral syndrome")
    void mapsAntipyretic() {
        DiagnosisInferenceResponse response = service.infer(requestFor("Paracetamol"));
        assertThat(response.diagnosis()).isEqualTo("Acute febrile / viral syndrome");
    }

    @Test
    @DisplayName("maps an antibiotic to a bacterial infection")
    void mapsAntibiotic() {
        DiagnosisInferenceResponse response = service.infer(requestFor("Amoxicillin"));
        assertThat(response.diagnosis()).isEqualTo("Bacterial infection");
    }

    @Test
    @DisplayName("maps a bronchodilator to obstructive airway disease")
    void mapsBronchodilator() {
        DiagnosisInferenceResponse response = service.infer(requestFor("Ventolin"));
        assertThat(response.diagnosis()).contains("airway disease");
    }

    @Test
    @DisplayName("keyword matching is case-insensitive")
    void caseInsensitiveKeyword() {
        DiagnosisInferenceResponse response = service.infer(requestFor("IBUPROFEN"));
        assertThat(response.diagnosis()).isEqualTo("Acute febrile / viral syndrome");
    }

    @Test
    @DisplayName("falls back to an indeterminate suggestion for an unknown drug")
    void unknownDrugIsIndeterminate() {
        DiagnosisInferenceResponse response = service.infer(requestFor("Xyzzymab"));
        assertThat(response.diagnosis()).contains("Indeterminate");
    }
}
