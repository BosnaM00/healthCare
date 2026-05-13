package org.example.healthcare.controller;

import lombok.RequiredArgsConstructor;
import org.example.healthcare.dto.booking.BookingResponse;
import org.example.healthcare.dto.medic.EarningsSummaryResponse;
import org.example.healthcare.dto.medic.EarningTransactionResponse;
import org.example.healthcare.dto.slot.SlotResponse;
import org.example.healthcare.model.Payment;
import org.example.healthcare.model.PaymentStatus;
import org.example.healthcare.repository.BookingRepository;
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
 * Medic-facing dashboard endpoints.
 * All routes require authentication; the authenticated user must have MEDIC role.
 */
@RestController
@RequestMapping("/api/v1/medic")
@RequiredArgsConstructor
public class MedicDashboardController {

    private static final Set<PaymentStatus> EARNED_STATUSES =
            Set.of(PaymentStatus.HELD, PaymentStatus.RELEASED);

    private final MedicRepository          medicRepository;
    private final BookingRepository        bookingRepository;
    private final PaymentRepository        paymentRepository;
    private final ConsultationRepository   consultationRepository;

    /**
     * GET /api/v1/medic/bookings/upcoming
     * Returns bookings with a future slot for the authenticated medic, ordered by slot start ASC.
     */
    @GetMapping("/bookings/upcoming")
    public ResponseEntity<List<BookingResponse>> getUpcomingBookings(
            @AuthenticationPrincipal AppUserDetails principal) {

        var medic = medicRepository.findByUserId(principal.getUserId())
                .orElse(null);
        if (medic == null) return ResponseEntity.ok(List.of());

        var bookings = bookingRepository
                .findUpcomingByMedicId(medic.getId(), Instant.now())
                .stream()
                .map(b -> {
                    String bookingStatus = switch (b.getPaymentStatus()) {
                        case PAID    -> "CONFIRMED";
                        case REFUNDED, FAILED -> "CANCELLED";
                        default      -> "SCHEDULED";
                    };
                    var user       = b.getMedic().getUser();
                    var medicInfo  = new BookingResponse.MedicInfo(
                            b.getMedic().getId(), user.getId(),
                            user.getFirstName(), user.getLastName());
                    return new BookingResponse(
                            b.getId(),
                            b.getPatient().getId(),
                            b.getMedic().getId(),
                            b.getSlot().getId(),
                            b.getConsultationType(),
                            b.getPaymentStatus(),
                            bookingStatus,
                            b.getCancellationPolicyAcceptedAt(),
                            b.getCreatedAt(),
                            new SlotResponse(
                                    b.getSlot().getId(),
                                    b.getSlot().getMedic().getId(),
                                    b.getSlot().getStartsAt(),
                                    b.getSlot().getEndsAt(),
                                    b.getSlot().getStatus()),
                            medicInfo);
                })
                .toList();

        return ResponseEntity.ok(bookings);
    }

    /**
     * GET /api/v1/medic/earnings/summary?period=current-month|last-month|current-year|all
     * Returns aggregated earnings for the authenticated medic.
     */
    @GetMapping("/earnings/summary")
    public ResponseEntity<EarningsSummaryResponse> getEarningsSummary(
            @AuthenticationPrincipal AppUserDetails principal,
            @RequestParam(defaultValue = "current-month") String period) {

        var medic = medicRepository.findByUserId(principal.getUserId())
                .orElse(null);
        if (medic == null) return ResponseEntity.ok(zeroSummary(period));

        var range = resolvePeriod(period);
        var payments = paymentRepository.findByMedicIdAndCreatedAtBetweenAndStatusIn(
                medic.getId(), range[0], range[1], EARNED_STATUSES);

        return ResponseEntity.ok(buildSummary(payments, period));
    }

    /**
     * GET /api/v1/medic/earnings/transactions?period=current-month|last-month|current-year|all
     * Returns individual payment records for the authenticated medic.
     */
    @GetMapping("/earnings/transactions")
    public ResponseEntity<List<EarningTransactionResponse>> getEarningsTransactions(
            @AuthenticationPrincipal AppUserDetails principal,
            @RequestParam(defaultValue = "current-month") String period) {

        var medic = medicRepository.findByUserId(principal.getUserId())
                .orElse(null);
        if (medic == null) return ResponseEntity.ok(List.of());

        var range = resolvePeriod(period);
        var payments = paymentRepository.findByMedicIdAndCreatedAtBetweenAndStatusIn(
                medic.getId(), range[0], range[1], EARNED_STATUSES);

        var transactions = payments.stream()
                .map(p -> toTransaction(p))
                .toList();

        return ResponseEntity.ok(transactions);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private EarningTransactionResponse toTransaction(Payment p) {
        var consultation = consultationRepository.findByBookingId(p.getBooking().getId()).orElse(null);
        String consultationId = consultation != null ? consultation.getId().toString() : null;

        var patient = p.getBooking().getPatient();
        String patientName = patient.getFirstName() + " " + patient.getLastName();

        BigDecimal net = p.getAmount().subtract(p.getPlatformFee());

        return new EarningTransactionResponse(
                p.getId(),
                consultationId,
                patientName,
                p.getCreatedAt(),
                p.getAmount(),
                p.getPlatformFee(),
                net,
                p.getCurrency(),
                p.getStatus().name()
        );
    }

    private EarningsSummaryResponse buildSummary(List<Payment> payments, String period) {
        if (payments.isEmpty()) return zeroSummary(period);

        BigDecimal totalGross = payments.stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalFee = payments.stream()
                .map(Payment::getPlatformFee)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalNet = totalGross.subtract(totalFee);
        long count = payments.size();
        BigDecimal avg = totalGross.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        String currency = payments.getFirst().getCurrency();

        return new EarningsSummaryResponse(totalGross, totalFee, totalNet, currency, period, count, avg);
    }

    private EarningsSummaryResponse zeroSummary(String period) {
        return new EarningsSummaryResponse(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "RON", period, 0, BigDecimal.ZERO);
    }

    /** Returns [startInclusive, endExclusive] for the given period name. */
    private Instant[] resolvePeriod(String period) {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        return switch (period) {
            case "current-month" -> new Instant[]{
                    now.with(TemporalAdjusters.firstDayOfMonth()).toLocalDate()
                            .atStartOfDay(ZoneOffset.UTC).toInstant(),
                    now.toInstant()
            };
            case "last-month" -> {
                ZonedDateTime start = now.minusMonths(1)
                        .with(TemporalAdjusters.firstDayOfMonth())
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
