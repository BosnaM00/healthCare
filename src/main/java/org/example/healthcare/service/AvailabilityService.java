package org.example.healthcare.service;

import org.example.healthcare.dto.availability.AvailabilityRequest;
import org.example.healthcare.dto.availability.AvailabilityResponse;

import java.util.List;
import java.util.UUID;

public interface AvailabilityService {

    List<AvailabilityResponse> getByMedic(UUID medicId);

    AvailabilityResponse create(UUID medicId, AvailabilityRequest request);

    AvailabilityResponse update(UUID medicId, UUID id, AvailabilityRequest request);

    void delete(UUID medicId, UUID id);
}
