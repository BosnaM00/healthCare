package org.example.healthcare.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.healthcare.common.exception.BusinessException;
import org.example.healthcare.common.exception.ResourceNotFoundException;
import org.example.healthcare.dto.specialty.SpecialtyRequest;
import org.example.healthcare.dto.specialty.SpecialtyResponse;
import org.example.healthcare.model.Specialty;
import org.example.healthcare.repository.MedicSpecialtyRepository;
import org.example.healthcare.repository.SpecialtyRepository;
import org.example.healthcare.service.SpecialtyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpecialtyServiceImpl implements SpecialtyService {

    private final SpecialtyRepository specialtyRepository;
    private final MedicSpecialtyRepository medicSpecialtyRepository;

    @Override
    public List<SpecialtyResponse> listAll() {
        return specialtyRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public SpecialtyResponse getById(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Override
    @Transactional
    public SpecialtyResponse create(SpecialtyRequest request) {
        if (specialtyRepository.existsByName(request.name()))
            throw new BusinessException("Specialty name already exists");

        Specialty specialty = Specialty.builder()
                .name(request.name())
                .description(request.description())
                .build();

        return toResponse(specialtyRepository.save(specialty));
    }

    @Override
    @Transactional
    public SpecialtyResponse update(UUID id, SpecialtyRequest request) {
        Specialty specialty = findOrThrow(id);
        specialty.setName(request.name());
        specialty.setDescription(request.description());
        return toResponse(specialty);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Specialty specialty = findOrThrow(id);
        if (medicSpecialtyRepository.existsBySpecialtyId(id))
            throw new BusinessException("Cannot delete specialty assigned to active medics");
        specialtyRepository.delete(specialty);
    }

    private Specialty findOrThrow(UUID id) {
        return specialtyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Specialty", id));
    }

    private SpecialtyResponse toResponse(Specialty specialty) {
        return new SpecialtyResponse(
                specialty.getId(),
                specialty.getName(),
                specialty.getDescription()
        );
    }
}
