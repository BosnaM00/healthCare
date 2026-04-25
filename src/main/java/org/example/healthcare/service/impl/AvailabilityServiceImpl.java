package org.example.healthcare.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.healthcare.common.exception.BusinessException;
import org.example.healthcare.common.exception.ResourceNotFoundException;
import org.example.healthcare.dto.availability.AvailabilityRequest;
import org.example.healthcare.dto.availability.AvailabilityResponse;
import org.example.healthcare.model.Availability;
import org.example.healthcare.model.Medic;
import org.example.healthcare.repository.AvailabilityRepository;
import org.example.healthcare.repository.MedicRepository;
import org.example.healthcare.service.AvailabilityService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AvailabilityServiceImpl implements AvailabilityService {

    private final AvailabilityRepository availabilityRepository;
    private final MedicRepository medicRepository;

    @Override
    public List<AvailabilityResponse> getByMedic(UUID medicId) {
        return availabilityRepository.findByMedicId(medicId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public AvailabilityResponse create(UUID medicId, AvailabilityRequest request) {
        validateWindow(request);
        validateNoOverlap(medicId, request.dayOfWeek(), request.startTime(), request.endTime(), null);

        Medic medic = medicRepository.findById(medicId)
                .orElseThrow(() -> new ResourceNotFoundException("Medic", medicId));

        Availability availability = Availability.builder()
                .medic(medic)
                .dayOfWeek(request.dayOfWeek())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .slotDurationMin(request.slotDurationMin())
                .bufferMin(request.bufferMin())
                .build();

        return toResponse(availabilityRepository.save(availability));
    }

    @Override
    @Transactional
    public AvailabilityResponse update(UUID medicId, UUID id, AvailabilityRequest request) {
        Availability availability = findOwnedOrThrow(id, medicId);
        validateWindow(request);
        validateNoOverlap(medicId, request.dayOfWeek(), request.startTime(), request.endTime(), id);

        availability.setDayOfWeek(request.dayOfWeek());
        availability.setStartTime(request.startTime());
        availability.setEndTime(request.endTime());
        availability.setSlotDurationMin(request.slotDurationMin());
        availability.setBufferMin(request.bufferMin());

        return toResponse(availability);
    }

    @Override
    @Transactional
    public void delete(UUID medicId, UUID id) {
        availabilityRepository.delete(findOwnedOrThrow(id, medicId));
    }

    private void validateWindow(AvailabilityRequest request) {
        if (!request.startTime().isBefore(request.endTime()))
            throw new BusinessException("startTime must be before endTime");
        int windowMinutes = (int) Duration.between(request.startTime(), request.endTime()).toMinutes();
        if (windowMinutes < request.slotDurationMin())
            throw new BusinessException("Window too short for slot duration");
    }

    private void validateNoOverlap(UUID medicId, DayOfWeek day, LocalTime start, LocalTime end, UUID excludeId) {
        if (availabilityRepository.existsOverlap(medicId, day, start, end, excludeId))
            throw new BusinessException("Time window overlaps existing availability");
    }

    private Availability findOwnedOrThrow(UUID id, UUID medicId) {
        Availability availability = availabilityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Availability", id));
        if (!availability.getMedic().getId().equals(medicId))
            throw new BusinessException("Availability does not belong to this medic");
        return availability;
    }

    private AvailabilityResponse toResponse(Availability a) {
        return new AvailabilityResponse(
                a.getId(),
                a.getMedic().getId(),
                a.getDayOfWeek(),
                a.getStartTime(),
                a.getEndTime(),
                a.getSlotDurationMin(),
                a.getBufferMin()
        );
    }
}
