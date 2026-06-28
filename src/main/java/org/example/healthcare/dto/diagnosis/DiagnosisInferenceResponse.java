package org.example.healthcare.dto.diagnosis;

/**
 * AI-inferred diagnosis suggestion. This is decision-support only — the medic
 * remains responsible for the final diagnosis. Never persisted.
 *
 * @param diagnosis  the most likely diagnosis suggested from the medications
 * @param reasoning  short rationale linking medications to the suggestion
 * @param confidence one of {@code low}, {@code medium}, {@code high}
 * @param disclaimer human-readable caveat to render alongside the suggestion
 * @param mock       true when produced by the offline stub (AI gate disabled)
 */
public record DiagnosisInferenceResponse(
        String diagnosis,
        String reasoning,
        String confidence,
        String disclaimer,
        boolean mock
) {}
