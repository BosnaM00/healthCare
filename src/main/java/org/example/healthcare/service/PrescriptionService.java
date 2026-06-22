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

    /** Patient retrieves all of their own decrypted prescriptions, newest first */
    List<PrescriptionResponse> getForPatient(UUID patientUserId);

    /**
     * Generates a PDF of the prescription on demand. Accessible to the patient it was issued to
     * or the treating medic; throws if the principal is neither.
     *
     * @return the rendered PDF as a byte array
     */
    byte[] generatePdf(UUID prescriptionId, UUID principalId);

    /** Called by async job after PDF is generated and uploaded to S3 */
    void setPdfKey(UUID prescriptionId, String s3Key);
}
