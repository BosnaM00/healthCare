package org.example.healthcare.service;

import org.example.healthcare.dto.prescription.PrescriptionRequest;
import org.example.healthcare.dto.prescription.PrescriptionResponse;

import java.util.List;
import java.util.UUID;

public interface PrescriptionService {

    /** Medic creates a prescription for a completed consultation */
    PrescriptionResponse create(UUID consultationId, UUID medicUserId, PrescriptionRequest request);

    /** Patient or medic retrieves decrypted prescriptions for a consultation (empty list if none issued yet) */
    List<PrescriptionResponse> getByConsultationId(UUID consultationId, UUID principalId);

    /** Called by async job after PDF is generated and uploaded to S3 */
    void setPdfKey(UUID prescriptionId, String s3Key);
}
