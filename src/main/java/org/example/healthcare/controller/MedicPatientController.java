package org.example.healthcare.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.example.healthcare.common.EncryptionService;
import org.example.healthcare.dto.medic.PatientDetailResponse;
import org.example.healthcare.dto.medic.PatientDocumentResponse;
import org.example.healthcare.dto.medic.PatientPrescriptionResponse;
import org.example.healthcare.dto.medic.TimelineEventResponse;
import org.example.healthcare.dto.medic.VitalReadingResponse;
import org.example.healthcare.model.Booking;
import org.example.healthcare.model.Consultation;
import org.example.healthcare.model.Medic;
import org.example.healthcare.model.Prescription;
import org.example.healthcare.model.User;
import org.example.healthcare.repository.BookingRepository;
import org.example.healthcare.repository.ConsultationRepository;
import org.example.healthcare.repository.MedicRepository;
import org.example.healthcare.repository.PrescriptionRepository;
import org.example.healthcare.security.AppUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Medic-facing "Patient record" endpoints.
 *
 * <p>Every route is scoped to the authenticated medic and authorizes access by
 * requiring at least one booking between the medic and the requested patient —
 * a medic can only view patients they have actually treated.
 *
 * <p>The current schema has no vitals or documents tables, so those endpoints
 * return empty lists. The timeline is derived from existing bookings,
 * consultations and prescriptions. Prescription content is stored as an
 * AES-256 GCM encrypted JSON blob; the prescriptions endpoint decrypts it and
 * parses the structured fields (diagnosis, notes, medications) for the treating
 * medic, who is already authorized to view it.
 */
@RestController
@RequestMapping("/api/v1/medic/patients")
@RequiredArgsConstructor
public class MedicPatientController {

    private final MedicRepository medicRepository;
    private final BookingRepository bookingRepository;
    private final ConsultationRepository consultationRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final EncryptionService encryptionService;

    /** Local mapper — parses the decrypted prescription JSON blob (no Spring bean needed). */
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // ── List ──────────────────────────────────────────────────────────────────

    /** GET /api/v1/medic/patients — every patient the medic has ever booked. */
    @GetMapping
    public ResponseEntity<List<PatientDetailResponse>> listPatients(
            @AuthenticationPrincipal AppUserDetails principal) {

        Medic medic = medicRepository.findByUserId(principal.getUserId()).orElse(null);
        if (medic == null) return ResponseEntity.ok(List.of());

        List<PatientDetailResponse> patients = bookingRepository
                .findDistinctPatientsByMedicId(medic.getId())
                .stream()
                .map(patient -> buildPatientDetail(medic.getId(), patient))
                .toList();

        return ResponseEntity.ok(patients);
    }

    // ── Detail ──────────────────────────────────────────────────────────────────

    /** GET /api/v1/medic/patients/{patientId} */
    @GetMapping("/{patientId}")
    public ResponseEntity<PatientDetailResponse> getPatient(
            @AuthenticationPrincipal AppUserDetails principal,
            @PathVariable String patientId) {

        Medic medic = medicRepository.findByUserId(principal.getUserId()).orElse(null);
        UUID pid = parseUuid(patientId);
        if (medic == null || pid == null
                || !bookingRepository.existsByMedicIdAndPatientId(medic.getId(), pid)) {
            return ResponseEntity.notFound().build();
        }

        return bookingRepository.findDistinctPatientsByMedicId(medic.getId()).stream()
                .filter(u -> u.getId().equals(pid))
                .findFirst()
                .map(patient -> ResponseEntity.ok(buildPatientDetail(medic.getId(), patient)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ── Vitals (no backing table → empty) ─────────────────────────────────────

    /** GET /api/v1/medic/patients/{patientId}/vitals */
    @GetMapping("/{patientId}/vitals")
    public ResponseEntity<List<VitalReadingResponse>> getVitals(
            @AuthenticationPrincipal AppUserDetails principal,
            @PathVariable String patientId) {

        if (!authorized(principal, patientId)) return ResponseEntity.ok(List.of());
        return ResponseEntity.ok(List.of());
    }

    // ── Documents (no backing table → empty) ──────────────────────────────────

    /** GET /api/v1/medic/patients/{patientId}/documents */
    @GetMapping("/{patientId}/documents")
    public ResponseEntity<List<PatientDocumentResponse>> getDocuments(
            @AuthenticationPrincipal AppUserDetails principal,
            @PathVariable String patientId) {

        if (!authorized(principal, patientId)) return ResponseEntity.ok(List.of());
        return ResponseEntity.ok(List.of());
    }

    // ── Prescriptions ─────────────────────────────────────────────────────────

    /** GET /api/v1/medic/patients/{patientId}/prescriptions */
    @GetMapping("/{patientId}/prescriptions")
    public ResponseEntity<List<PatientPrescriptionResponse>> getPrescriptions(
            @AuthenticationPrincipal AppUserDetails principal,
            @PathVariable String patientId) {

        Medic medic = medicRepository.findByUserId(principal.getUserId()).orElse(null);
        UUID pid = parseUuid(patientId);
        if (medic == null || pid == null
                || !bookingRepository.existsByMedicIdAndPatientId(medic.getId(), pid)) {
            return ResponseEntity.ok(List.of());
        }

        List<PatientPrescriptionResponse> prescriptions = prescriptionRepository
                .findByMedicIdAndPatientId(medic.getId(), pid)
                .stream()
                .map(this::toPrescription)
                .toList();

        return ResponseEntity.ok(prescriptions);
    }

    // ── Timeline ──────────────────────────────────────────────────────────────

    /** GET /api/v1/medic/patients/{patientId}/timeline */
    @GetMapping("/{patientId}/timeline")
    public ResponseEntity<List<TimelineEventResponse>> getTimeline(
            @AuthenticationPrincipal AppUserDetails principal,
            @PathVariable String patientId) {

        Medic medic = medicRepository.findByUserId(principal.getUserId()).orElse(null);
        UUID pid = parseUuid(patientId);
        if (medic == null || pid == null
                || !bookingRepository.existsByMedicIdAndPatientId(medic.getId(), pid)) {
            return ResponseEntity.ok(List.of());
        }

        UUID medicId = medic.getId();
        List<TimelineEventResponse> events = new ArrayList<>();

        for (Consultation c : consultationRepository.findByMedicIdAndPatientId(medicId, pid)) {
            Instant ts = c.getStartedAt() != null ? c.getStartedAt() : c.getEndedAt();
            events.add(new TimelineEventResponse(
                    "consult-" + c.getId(),
                    "CONSULTATION",
                    ts,
                    "Video consultation",
                    c.getStatus() != null ? c.getStatus().name() : null,
                    c.getId().toString()));
        }

        for (Prescription p : prescriptionRepository.findByMedicIdAndPatientId(medicId, pid)) {
            events.add(new TimelineEventResponse(
                    "rx-" + p.getId(),
                    "PRESCRIPTION",
                    p.getCreatedAt(),
                    "Prescription issued",
                    null,
                    p.getId().toString()));
        }

        for (Booking b : bookingRepository.findByMedicIdAndPatientId(medicId, pid)) {
            events.add(new TimelineEventResponse(
                    "booking-" + b.getId(),
                    "BOOKING",
                    b.getCreatedAt(),
                    "Booking created",
                    null,
                    b.getId().toString()));
        }

        events.sort(Comparator.comparing(
                TimelineEventResponse::timestamp,
                Comparator.nullsLast(Comparator.reverseOrder())));

        return ResponseEntity.ok(events);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean authorized(AppUserDetails principal, String patientId) {
        Medic medic = medicRepository.findByUserId(principal.getUserId()).orElse(null);
        UUID pid = parseUuid(patientId);
        return medic != null && pid != null
                && bookingRepository.existsByMedicIdAndPatientId(medic.getId(), pid);
    }

    private PatientDetailResponse buildPatientDetail(UUID medicId, User patient) {
        UUID patientId = patient.getId();

        Instant lastConsultationAt = consultationRepository
                .findByMedicIdAndPatientId(medicId, patientId).stream()
                .map(c -> c.getEndedAt() != null ? c.getEndedAt() : c.getStartedAt())
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        Instant now = Instant.now();
        Instant nextBookingAt = bookingRepository
                .findByMedicIdAndPatientId(medicId, patientId).stream()
                .map(b -> b.getSlot().getStartsAt())
                .filter(s -> s != null && s.isAfter(now))
                .min(Comparator.naturalOrder())
                .orElse(null);

        return new PatientDetailResponse(
                patientId.toString(),
                patientId.toString(),
                patient.getFirstName(),
                patient.getLastName(),
                null,            // dateOfBirth — not stored
                null,            // bloodType — not stored
                List.of(),       // allergies — not stored
                patient.getEmail(),
                null,            // phone — not stored
                null,            // avatarUrl — not stored
                null,            // insurerName — not stored
                null,            // insurancePolicyNumber — not stored
                lastConsultationAt,
                nextBookingAt,
                List.of());      // activeConditions — not stored
    }

    private PatientPrescriptionResponse toPrescription(Prescription p) {
        Booking booking = p.getConsultation().getBooking();

        // Content is an AES-256 GCM encrypted JSON blob serialized by the frontend:
        //   { diagnosis, notes?, medications: [{ name, dosage, unit, frequency,
        //     durationDays, instructions?, interactionWarning? }], ... }
        // Decrypt and parse it so the medic's patient-record page can render the
        // structured fields. Falls back gracefully on legacy / plain-text content.
        String diagnosis = null;
        String notes = null;
        List<PatientPrescriptionResponse.MedicationResponse> medications = List.of();

        try {
            String content = encryptionService.decrypt(p.getContentEncrypted());
            JsonNode root = objectMapper.readTree(content);

            diagnosis = textOrNull(root, "diagnosis");
            notes = textOrNull(root, "notes");

            JsonNode meds = root.path("medications");
            if (meds.isArray()) {
                List<PatientPrescriptionResponse.MedicationResponse> parsed = new ArrayList<>();
                int i = 0;
                for (JsonNode m : meds) {
                    parsed.add(new PatientPrescriptionResponse.MedicationResponse(
                            m.hasNonNull("id") ? m.get("id").asText() : "med-" + i,
                            textOrEmpty(m, "name"),
                            textOrEmpty(m, "dosage"),
                            textOrEmpty(m, "unit"),
                            textOrEmpty(m, "frequency"),
                            m.path("durationDays").asInt(0),
                            textOrNull(m, "instructions"),
                            textOrNull(m, "interactionWarning")));
                    i++;
                }
                medications = parsed;
            }
        } catch (Exception e) {
            // Decryption or JSON parse failure — surface nothing rather than 500.
            // Legacy plain-text content simply yields no structured fields.
        }

        return new PatientPrescriptionResponse(
                p.getId().toString(),
                p.getConsultation().getId().toString(),
                booking.getPatient().getId().toString(),
                booking.getMedic().getId().toString(),
                p.getCreatedAt(),
                null,            // expiresAt — not stored separately
                medications,
                diagnosis,
                notes,
                null);           // signatureUrl — not stored
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null && !v.isNull() ? v.asText() : null;
    }

    private static String textOrEmpty(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null && !v.isNull() ? v.asText() : "";
    }

    private UUID parseUuid(String value) {
        try {
            return Optional.ofNullable(value).map(UUID::fromString).orElse(null);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
