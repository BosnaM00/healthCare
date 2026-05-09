package org.example.healthcare.controller;

import lombok.RequiredArgsConstructor;
import org.example.healthcare.dto.medic.EarningsSummaryResponse;
import org.example.healthcare.dto.medic.EarningTransactionResponse;
import org.example.healthcare.model.Payment;
import org.example.healthcare.model.PaymentStatus;
import org.example.healthcare.repository.ClinicRepository;
import org.example.healthcare.repository.ConsultationRepository;
import org.example.healthcare.repository.MedicRepository;
import org.example.healthcare.repository.PaymentRepository;
import org.example.healthcare.security.AppUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Set;

/**
 * Clinic-manager-facing dashboard endpoints.
 * Resolves the clinic from the authenticated medic's clinic affiliation.
 */
@RestController
@RequestMapping("/api/v1/clinic")
@RequiredArgsConstructor
public class ClinicDashboardController {

    private static final Set<PaymentStatus> EARNED_STATUSES =
            Set.of(PaymentStatus.HELD, PaymentStatus.RELEASED);

    private final MedicRepository        medicRepository;
    private final PaymentRepository      paymentRepository;
    private final ConsultationRepository consultationRepository;

    /**
     * GET /api/v1/clinic/earnings/summary?period=current-month|last-month|current-year|all
     */
    @GetMapping("/earnings/summary")
    public ResponseEntity<EarningsSummaryResponse> getEarningsSummary(
            @AuthenticationPrincipal AppUserDetails principal,
            @RequestParam(defaultValue = "current-month") String period) {

        var clinicId = resolveClinicId(principal);
        if (clinicId == null) return ResponseEntity.ok(zeroSummary(period));

        var range = resolvePeriod(period);
        var payments = paymentRepository.findByClinicIdAndCreatedAtBetweenAndStatusIn(
                clinicId, range[0], range[1], EARNED_STATUSES);

        return ResponseEntity.ok(buildSummary(payments, period));
    }

    /**
     * GET /api/v1/clinic/earnings/transactions?period=current-month|last-month|current-year|all
     */
    @GetMapping("/earnings/transactions")
    public ResponseEntity<List<EarningTransactionResponse>> getEarningsTransactions(
            @AuthenticationPrincipal AppUserDetails principal,
            @RequestParam(defaultValue = "current-month") String period) {

        var clinicId = resolveClinicId(principal);
        if (clinicId == null) return ResponseEntity.ok(List.of());

        var range = resolvePeriod(period);
        var payments = paymentRepository.findByClinicIdAndCreatedAtBetweenAndStatusIn(
                clinicId, range[0], range[1], EARNED_STATUSES);

        return ResponseEntity.ok(payments.stream().map(this::toTransaction).toList());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Returns the clinic ID linked to the authenticated user's medic profile, or null. */
    private java.util.UUID resolveClinicId(AppUserDetails principal) {
        return medicRepository.findByUserId(principal.getUserId())
                .map(m -> m.getClinic() != null ? m.getClinic().getId() : null)
                .orElse(null);
    }

    private EarningTransactionResponse toTransaction(Payment p) {
        var consultation = consultationRepository.findByBookingId(p.getBooking().getId()).orElse(null);
        String consultationId = consultation != null ? consultation.getId().toString() : null;
        var patient = p.getBooking().getPatient();
        String patientName = patient.getFirstName() + " " + patient.getLastName();
        BigDecimal net = p.getAmount().subtract(p.getPlatformFee());
        return new EarningTransactionResponse(
                p.getId(), consultationId, patientName, p.getCreatedAt(),
                p.getAmount(), p.getPlatformFee(), net, p.getCurrency(), p.getStatus().name());
    }

    private EarningsSummaryResponse buildSummary(List<Payment> payments, String period) {
        if (payments.isEmpty()) return zeroSummary(period);
        BigDecimal totalGross = payments.stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalFee   = payments.stream().map(Payment::getPlatformFee).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalNet   = totalGross.subtract(totalFee);
        long count            = payments.size();
        BigDecimal avg        = totalGross.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        return new EarningsSummaryResponse(totalGross, totalFee, totalNet,
                payments.getFirst().getCurrency(), period, count, avg);
    }

    private EarningsSummaryResponse zeroSummary(String period) {
        return new EarningsSummaryResponse(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "RON", period, 0, BigDecimal.ZERO);
    }

    private Instant[] resolvePeriod(String period) {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        return switch (period) {
            case "current-month" -> new Instant[]{
                    now.with(TemporalAdjusters.firstDayOfMonth()).toLocalDate()
                            .atStartOfDay(ZoneOffset.UTC).toInstant(),
                    now.toInstant()
            };
            case "last-month" -> {
                ZonedDateTime start = now.minusMonths(1).with(TemporalAdjusters.firstDayOfMonth())
                        .toLocalDate().atStartOfDay(ZoneOffset.UTC);
                ZonedDateTime end = now.with(TemporalAdjusters.firstDayOfMonth())
                        .toLocalDate().atStartOfDay(ZoneOffset.UTC);
                yield new Instant[]{start.toInstant(), end.toInstant()};
            }
            case "current-year" -> new Instant[]{
                    now.with(TemporalAdjusters.firstDayOfYear()).toLocalDate()
                            .atStartOfDay(ZoneOffset.UTC).toInstant(),
                    now.toInstant()
            };
            default -> new Instant[]{Instant.EPOCH, now.toInstant()};
        };
    }
}
