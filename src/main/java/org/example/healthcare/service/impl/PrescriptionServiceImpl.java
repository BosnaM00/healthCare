package org.example.healthcare.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.example.healthcare.common.EncryptionService;
import org.example.healthcare.common.PdfWriter;
import org.example.healthcare.common.exception.BusinessException;
import org.example.healthcare.common.exception.ResourceNotFoundException;
import org.example.healthcare.dto.prescription.PrescriptionRequest;
import org.example.healthcare.dto.prescription.PrescriptionResponse;
import org.example.healthcare.model.Booking;
import org.example.healthcare.model.Consultation;
import org.example.healthcare.model.ConsultationStatus;
import org.example.healthcare.model.Prescription;
import org.example.healthcare.repository.ConsultationRepository;
import org.example.healthcare.repository.PrescriptionRepository;
import org.example.healthcare.service.PrescriptionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PrescriptionServiceImpl implements PrescriptionService {

    private final PrescriptionRepository prescriptionRepository;
    private final ConsultationRepository consultationRepository;
    private final EncryptionService      encryptionService;

    /** Local mapper — parses the decrypted prescription JSON blob (no Spring bean needed). */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy").withZone(ZoneId.systemDefault());

    @Override
    @Transactional
    public PrescriptionResponse create(UUID consultationId, UUID medicUserId, PrescriptionRequest request) {
        Consultation consultation = consultationRepository.findById(consultationId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation", consultationId));

        if (!consultation.getBooking().getMedic().getUser().getId().equals(medicUserId))
            throw new BusinessException("Only the treating medic can create a prescription");

        if (consultation.getStatus() != ConsultationStatus.COMPLETED
                && consultation.getStatus() != ConsultationStatus.IN_PROGRESS)
            throw new BusinessException("Prescription requires an active or completed consultation");

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
    public List<PrescriptionResponse> getForPatient(UUID patientUserId) {
        return prescriptionRepository
                .findByConsultation_Booking_Patient_IdOrderByCreatedAtDesc(patientUserId)
                .stream()
                .map(prescription -> toResponse(
                        prescription, encryptionService.decrypt(prescription.getContentEncrypted())))
                .toList();
    }

    @Override
    @Transactional
    public void setPdfKey(UUID prescriptionId, String s3Key) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Prescription", prescriptionId));
        prescription.setPdfS3Key(s3Key);
    }

    @Override
    public byte[] generatePdf(UUID prescriptionId, UUID principalId) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Prescription", prescriptionId));

        Booking booking = prescription.getConsultation().getBooking();
        boolean isPatient = booking.getPatient().getId().equals(principalId);
        boolean isMedic   = booking.getMedic().getUser().getId().equals(principalId);
        if (!isPatient && !isMedic)
            throw new BusinessException("Access denied");

        String content = encryptionService.decrypt(prescription.getContentEncrypted());
        return PdfWriter.build(buildPdfLines(prescription, booking, content));
    }

    /** Formats a prescription into the ordered text lines rendered by {@link PdfWriter}. */
    private List<PdfWriter.Line> buildPdfLines(Prescription p, Booking booking, String content) {
        List<PdfWriter.Line> lines = new ArrayList<>();

        // Header
        lines.add(PdfWriter.Line.bold("MediConnect", 20f));
        lines.add(PdfWriter.Line.of("Electronic Prescription", 12f, 4f));

        String patientName = fullName(
                booking.getPatient().getFirstName(), booking.getPatient().getLastName());
        String medicName = fullName(
                booking.getMedic().getUser().getFirstName(), booking.getMedic().getUser().getLastName());

        lines.add(PdfWriter.Line.of("Patient:  " + patientName, 11f, 18f));
        lines.add(PdfWriter.Line.of("Prescriber:  Dr. " + medicName, 11f, 2f));
        lines.add(PdfWriter.Line.of("Issued:  "
                + (p.getCreatedAt() != null ? DATE_FMT.format(p.getCreatedAt()) : "—"), 11f, 2f));
        lines.add(PdfWriter.Line.of("Reference:  " + p.getId(), 9f, 2f));

        // Structured fields (best-effort parse of the JSON content blob)
        try {
            JsonNode root = OBJECT_MAPPER.readTree(content);

            String diagnosis = text(root, "diagnosis");
            if (diagnosis != null && !diagnosis.isBlank()) {
                lines.add(PdfWriter.Line.bold("Diagnosis", 12f, 20f));
                lines.add(PdfWriter.Line.of(diagnosis, 11f, 4f));
            }

            JsonNode meds = root.path("medications");
            if (meds.isArray() && meds.size() > 0) {
                lines.add(PdfWriter.Line.bold("Medications", 12f, 20f));
                int i = 1;
                for (JsonNode m : meds) {
                    String name = text(m, "name");
                    String dosage = join(text(m, "dosage"), text(m, "unit"));
                    String freq = text(m, "frequency");
                    int duration = m.path("durationDays").asInt(0);

                    StringBuilder head = new StringBuilder();
                    head.append(i++).append(". ").append(name == null ? "" : name);
                    lines.add(PdfWriter.Line.bold(head.toString(), 11f, 8f));

                    StringBuilder detail = new StringBuilder();
                    if (!dosage.isBlank()) detail.append(dosage);
                    if (freq != null && !freq.isBlank())
                        detail.append(detail.length() > 0 ? "  -  " : "").append(freq);
                    if (duration > 0)
                        detail.append(detail.length() > 0 ? "  -  " : "").append(duration).append(" days");
                    if (detail.length() > 0)
                        lines.add(PdfWriter.Line.of(detail.toString(), 10f, 1f));

                    String instructions = text(m, "instructions");
                    if (instructions != null && !instructions.isBlank())
                        lines.add(PdfWriter.Line.of(instructions, 9f, 1f));

                    String warning = text(m, "interactionWarning");
                    if (warning != null && !warning.isBlank())
                        lines.add(PdfWriter.Line.of("! " + warning, 9f, 1f));
                }
            }

            String notes = text(root, "notes");
            if (notes != null && !notes.isBlank()) {
                lines.add(PdfWriter.Line.bold("Notes", 12f, 20f));
                lines.add(PdfWriter.Line.of(notes, 11f, 4f));
            }
        } catch (Exception e) {
            // Legacy / plain-text content — render it verbatim so nothing is lost.
            lines.add(PdfWriter.Line.bold("Prescription", 12f, 20f));
            lines.add(PdfWriter.Line.of(content, 11f, 4f));
        }

        lines.add(PdfWriter.Line.of(
                "This prescription was generated electronically by MediConnect.", 8f, 32f));
        return lines;
    }

    private static String fullName(String first, String last) {
        return ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
    }

    private static String join(String dosage, String unit) {
        return ((dosage == null ? "" : dosage) + " " + (unit == null ? "" : unit)).trim();
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null && !v.isNull() ? v.asText() : null;
    }

    private PrescriptionResponse toResponse(Prescription p, String content) {
        // TODO: generate pre-signed S3 URL when S3Client is wired
        String pdfUrl = p.getPdfS3Key() != null ? "s3://" + p.getPdfS3Key() : null;
        return new PrescriptionResponse(
                p.getId(), p.getConsultation().getId(), content, pdfUrl, p.getCreatedAt());
    }
}
