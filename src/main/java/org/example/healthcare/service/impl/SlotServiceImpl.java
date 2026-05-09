package org.example.healthcare.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.healthcare.common.exception.BusinessException;
import org.example.healthcare.common.exception.ResourceNotFoundException;
import org.example.healthcare.dto.slot.SlotResponse;
import org.example.healthcare.model.Availability;
import org.example.healthcare.model.Medic;
import org.example.healthcare.model.Slot;
import org.example.healthcare.model.SlotStatus;
import org.example.healthcare.repository.AvailabilityRepository;
import org.example.healthcare.repository.MedicRepository;
import org.example.healthcare.repository.SlotRepository;
import org.example.healthcare.service.SlotService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SlotServiceImpl implements SlotService {

    private final SlotRepository slotRepository;
    private final MedicRepository medicRepository;
    private final AvailabilityRepository availabilityRepository;

    @Override
    public List<SlotResponse> getAvailableSlots(UUID medicId, LocalDate from, LocalDate to) {
        validateDateRange(from, to);
        Instant start = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end   = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return slotRepository
                .findByMedicIdAndStatusAndStartsAtBetween(medicId, SlotStatus.AVAILABLE, start, end)
                .stream().map(this::toResponse).toList();
    }

    @Override
    public List<SlotResponse> getMedicOwnSlots(UUID medicId, LocalDate from, LocalDate to) {
        validateDateRange(from, to);
        Instant start = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end   = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return slotRepository
                .findByMedicIdAndStartsAtBetween(medicId, start, end)
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public SlotResponse blockSlot(UUID medicId, UUID slotId) {
        Slot slot = slotRepository.findByIdAndMedicId(slotId, medicId)
                .orElseThrow(() -> new ResourceNotFoundException("Slot", slotId));
        if (slot.getStatus() == SlotStatus.BOOKED)
            throw new BusinessException("Cannot block a slot that is already booked");
        slot.setStatus(SlotStatus.BLOCKED);
        return toResponse(slot);
    }

    @Override
    @Transactional
    public SlotResponse unblockSlot(UUID medicId, UUID slotId) {
        Slot slot = slotRepository.findByIdAndMedicId(slotId, medicId)
                .orElseThrow(() -> new ResourceNotFoundException("Slot", slotId));
        if (slot.getStatus() != SlotStatus.BLOCKED)
            throw new BusinessException("Slot is not blocked");
        slot.setStatus(SlotStatus.AVAILABLE);
        return toResponse(slot);
    }

    @Override
    @Transactional
    public void generateSlotsForDate(UUID medicId, LocalDate date) {
        Medic medic = medicRepository.findById(medicId)
                .orElseThrow(() -> new ResourceNotFoundException("Medic", medicId));

        List<Availability> windows = availabilityRepository
                .findByMedicIdAndDayOfWeek(medicId, date.getDayOfWeek());

        Instant dayStart = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant dayEnd   = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Set<Instant> existing = slotRepository
                .findByMedicIdAndStartsAtBetween(medicId, dayStart, dayEnd)
                .stream().map(Slot::getStartsAt).collect(Collectors.toSet());

        List<Slot> newSlots = new ArrayList<>();
        for (Availability av : windows) {
            LocalDateTime cursor    = date.atTime(av.getStartTime());
            LocalDateTime endWindow = date.atTime(av.getEndTime());
            int stepMinutes         = av.getSlotDurationMin() + av.getBufferMin();

            while (!cursor.plusMinutes(av.getSlotDurationMin()).isAfter(endWindow)) {
                Instant slotStart = cursor.toInstant(ZoneOffset.UTC);
                if (!existing.contains(slotStart)) {
                    newSlots.add(Slot.builder()
                            .medic(medic)
                            .startsAt(slotStart)
                            .endsAt(cursor.plusMinutes(av.getSlotDurationMin()).toInstant(ZoneOffset.UTC))
                            .status(SlotStatus.AVAILABLE)
                            .build());
                }
                cursor = cursor.plusMinutes(stepMinutes);
            }
        }

        if (!newSlots.isEmpty())
            slotRepository.saveAll(newSlots);
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (to.isAfter(from.plusDays(60)))
            throw new BusinessException("Date range cannot exceed 60 days");
    }

    private SlotResponse toResponse(Slot slot) {
        return new SlotResponse(
                slot.getId(),
                slot.getMedic().getId(),
                slot.getStartsAt(),   // maps to JSON "startTime"
                slot.getEndsAt(),     // maps to JSON "endTime"
                slot.getStatus()
        );
    }
}
