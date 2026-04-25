package org.example.healthcare.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.healthcare.dto.booking.BookingRequest;
import org.example.healthcare.dto.booking.BookingResponse;
import org.example.healthcare.service.BookingService;
import org.example.healthcare.service.MedicService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final MedicService medicService;

    /**
     * POST /api/v1/bookings
     * Create a booking — reserves slot and creates payment stub (PATIENT).
     */
    @PostMapping
    public ResponseEntity<BookingResponse> create(
            @RequestHeader("X-User-Id") UUID patientId,
            @Valid @RequestBody BookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bookingService.create(patientId, request));
    }

    /**
     * GET /api/v1/bookings/{id}
     * Get a single booking — accessible to the owning patient or medic (AUTH).
     */
    @GetMapping("/{id}")
    public ResponseEntity<BookingResponse> getById(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID principalId) {
        return ResponseEntity.ok(bookingService.getById(id, principalId));
    }

    /**
     * GET /api/v1/bookings/my?page=&size=
     * Patient's own booking history (PATIENT).
     */
    @GetMapping("/my")
    public ResponseEntity<Page<BookingResponse>> getMyBookings(
            @RequestHeader("X-User-Id") UUID patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                bookingService.getPatientBookings(
                        patientId, PageRequest.of(page, size, Sort.by("createdAt").descending())));
    }

    /**
     * DELETE /api/v1/bookings/{id}
     * Cancel a booking — triggers refund logic (PATIENT).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID patientId) {
        bookingService.cancel(id, patientId);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/v1/bookings/medic?page=&size=
     * Authenticated medic's booking list (MEDIC).
     */
    @GetMapping("/medic")
    public ResponseEntity<Page<BookingResponse>> getMedicBookings(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID medicId = medicService.getByUserId(userId).id();
        return ResponseEntity.ok(
                bookingService.getMedicBookings(
                        medicId, PageRequest.of(page, size, Sort.by("createdAt").descending())));
    }
}
