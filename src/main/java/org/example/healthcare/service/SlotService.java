package org.example.healthcare.service;

import org.example.healthcare.dto.slot.SlotResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface SlotService {

    List<SlotResponse> getAvailableSlots(UUID medicId, LocalDate from, LocalDate to);

    List<SlotResponse> getMedicOwnSlots(UUID medicId, LocalDate from, LocalDate to);

    SlotResponse blockSlot(UUID medicId, UUID slotId);

    SlotResponse unblockSlot(UUID medicId, UUID slotId);

    /** Called internally by the Quartz slot-generation job — not exposed via REST. */
    void generateSlotsForDate(UUID medicId, LocalDate date);
}
