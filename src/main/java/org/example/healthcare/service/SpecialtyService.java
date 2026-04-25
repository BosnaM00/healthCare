package org.example.healthcare.service;

import org.example.healthcare.dto.specialty.SpecialtyRequest;
import org.example.healthcare.dto.specialty.SpecialtyResponse;

import java.util.List;
import java.util.UUID;

public interface SpecialtyService {

    List<SpecialtyResponse> listAll();

    SpecialtyResponse getById(UUID id);

    SpecialtyResponse create(SpecialtyRequest request);

    SpecialtyResponse update(UUID id, SpecialtyRequest request);

    void delete(UUID id);
}
