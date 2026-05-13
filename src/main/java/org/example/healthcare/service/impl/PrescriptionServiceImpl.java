package org.example.healthcare.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.healthcare.common.EncryptionService;
import org.example.healthcare.common.exception.BusinessException;
import org.example.healthcare.common.exception.ResourceNotFoundException;
import org.example.healthcare.dto.prescription.PrescriptionRequest;
import org.example.healthcare.dto.prescription.PrescriptionResponse;
import org.example.healthcare.model.Consultation;
import org.example.healthcare.model.ConsultationStatus;
import org.example.healthcare.model.Prescription;
import org.example.healthcare.repository.ConsultationRepository;
import org.example.healthcare.repository.PrescriptionRepository;
import org.example.healthcare.service.PrescriptionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PrescriptionServiceImpl implements PrescriptionService {

    private final PrescriptionRepository prescriptionRepository;
    private final ConsultationRepository consultationRepository;
    private final EncryptionService      encryptionService;

    @Override
    @Transactional
    public PrescriptionResponse create(UUID consultationId, UUID medicUserId, PrescriptionRequest request) {
        Consultation consultation = consultationRepository.findById(consultationId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation", consultationId));

        if (!consultation.getBooking().getMedic().getUser().getId().equals(medicUserId))
            throw new BusinessException("Only the treating medic can create a prescription");

        if (consultation.getStatus() != ConsultationStatus.COMPLETED)
            throw new BusinessException("Prescription requires a COMPLETED consultation");

        if (prescriptionRepository.findByConsultationId(consultationId).isPresent())
            throw new BusinessException("A prescription already exists for this consultation");

        Prescription prescription = Prescription.builder()
                .consultation(consultation)
                .contentEncrypted(encryptionService.encrypt(request.content()))
                .build();

        Prescription saved = prescriptionRepository.save(prescription);
        return toResponse(saved, request.content());
    }

    @Override
    public List<PrescriptionResponse> getByConsultationId(UUID consultationId, UUID principalId) {
        return prescriptionRepository.findByConsultationId(consultationId)
                .map(prescription -> {
                    boolean isPatient = prescription.getConsultation().getBooking().getPatient().getId().equals(principalId);
                    boolean isMedic   = prescription.getConsultation().getBooking().getMedic().getUser().getId().equals(principalId);
                    if (!isPatient && !isMedic)
                        throw new BusinessException("Access denied");
                    String content = encryptionService.decrypt(prescription.getContentEncrypted());
                    return List.of(toResponse(prescription, content));
                })
                .orElse(List.of());
    }

    @Override
    @Transactional
    public void setPdfKey(UUID prescriptionId, String s3Key) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Prescription", prescriptionId));
        prescription.setPdfS3Key(s3Key);
    }

    private PrescriptionResponse toResponse(Prescription p, String content) {
        // TODO: generate pre-signed S3 URL when S3Client is wired
        String pdfUrl = p.getPdfS3Key() != null ? "s3://" + p.getPdfS3Key() : null;
        return new PrescriptionResponse(
                p.getId(), p.getConsultation().getId(), content, pdfUrl, p.getCreatedAt());
    }
}
