package org.example.healthcare.controller;

import lombok.RequiredArgsConstructor;
import org.example.healthcare.dto.slot.SlotResponse;
import org.example.healthcare.security.AppUserDetails;
import org.example.healthcare.service.MedicService;
import org.example.healthcare.service.SlotService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class SlotController {

    private final SlotService slotService;
    private final MedicService medicService;

    /**
     * GET /api/v1/medics/{id}/slots?from=&to=
     * Available slots for a medic within a date range (AUTH).
     */
    @GetMapping("/api/v1/medics/{id}/slots")
    public ResponseEntity<List<SlotResponse>> getAvailableSlots(
            @PathVariable UUID id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(slotService.getAvailableSlots(id, from, to));
    }

    /**
     * GET /api/v1/medics/me/slots?from=&to=
     * Authenticated medic's own slots with full status visibility (MEDIC).
     */
    @GetMapping("/api/v1/medics/me/slots")
    public ResponseEntity<List<SlotResponse>> getMySlots(
            @AuthenticationPrincipal AppUserDetails principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        UUID medicId = medicService.getByUserId(principal.getUserId()).id();
        return ResponseEntity.ok(slotService.getMedicOwnSlots(medicId, from, to));
    }

    /**
     * PATCH /api/v1/medics/me/slots/{slotId}/block
     * Block a slot (holiday / personal) (MEDIC).
     */
    @PatchMapping("/api/v1/medics/me/slots/{slotId}/block")
    public ResponseEntity<SlotResponse> blockSlot(
            @AuthenticationPrincipal AppUserDetails principal,
            @PathVariable UUID slotId) {
        UUID medicId = medicService.getByUserId(principal.getUserId()).id();
        return ResponseEntity.ok(slotService.blockSlot(medicId, slotId));
    }

    /**
     * PATCH /api/v1/medics/me/slots/{slotId}/unblock
     * Unblock a previously blocked slot (MEDIC).
     */
    @PatchMapping("/api/v1/medics/me/slots/{slotId}/unblock")
    public ResponseEntity<SlotResponse> unblockSlot(
            @AuthenticationPrincipal AppUserDetails principal,
            @PathVariable UUID slotId) {
        UUID medicId = medicService.getByUserId(principal.getUserId()).id();
        return ResponseEntity.ok(slotService.unblockSlot(medicId, slotId));
    }
}
