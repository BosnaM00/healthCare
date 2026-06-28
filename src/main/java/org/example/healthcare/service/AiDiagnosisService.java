package org.example.healthcare.service;

import org.example.healthcare.dto.diagnosis.DiagnosisInferenceRequest;
import org.example.healthcare.dto.diagnosis.DiagnosisInferenceResponse;

public interface AiDiagnosisService {

    /**
     * Infers a likely diagnosis from a set of prescribed medications.
     *
     * <p>Stateless decision-support: the result is returned to the caller and
     * never stored. When the AI feature gate is disabled, a deterministic mock
     * suggestion is returned instead of calling the model.
     */
    DiagnosisInferenceResponse infer(DiagnosisInferenceRequest request);
}
