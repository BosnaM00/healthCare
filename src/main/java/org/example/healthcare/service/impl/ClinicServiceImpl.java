package org.example.healthcare.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.healthcare.common.exception.BusinessException;
import org.example.healthcare.common.exception.ResourceNotFoundException;
import org.example.healthcare.dto.clinic.ClinicRequest;
import org.example.healthcare.dto.clinic.ClinicResponse;
import org.example.healthcare.dto.clinic.ClinicStatusRequest;
import org.example.healthcare.model.Clinic;
import org.example.healthcare.repository.ClinicRepository;
import org.example.healthcare.service.ClinicService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClinicServiceImpl implements ClinicService {

    private static final BigDecimal DEFAULT_COMMISSION = new BigDecimal("0.1500");

    private final ClinicRepository clinicRepository;

    @Override
    @Transactional
    public ClinicResponse create(ClinicRequest request) {
        if (clinicRepository.existsByCui(request.cui()))
            throw new BusinessException("A clinic with CUI " + request.cui() + " already exists");

        Clinic clinic = Clinic.builder()
                .name(request.name())
                .cui(request.cui())
                .commissionRate(request.commissionRate() != null ? request.commissionRate() : DEFAULT_COMMISSION)
                .build();
        return toResponse(clinicRepository.save(clinic));
    }

    @Override
    public ClinicResponse getById(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Override
    public Page<ClinicResponse> listAll(Pageable pageable) {
        return clinicRepository.findAll(pageable).map(this::toResponse);
    }

    @Override
    @Transactional
    public ClinicResponse update(UUID id, ClinicRequest request) {
        Clinic clinic = findOrThrow(id);
        clinic.setName(request.name());
        if (request.commissionRate() != null) clinic.setCommissionRate(request.commissionRate());
        return toResponse(clinic);
    }

    @Override
    @Transactional
    public ClinicResponse updateStatus(UUID id, ClinicStatusRequest request) {
        Clinic clinic = findOrThrow(id);
        clinic.setStatus(request.status());
        return toResponse(clinic);
    }

    @Override
    @Transactional
    public ClinicResponse setStripeAccount(UUID id, String stripeAccountId) {
        Clinic clinic = findOrThrow(id);
        clinic.setStripeAccountId(stripeAccountId);
        return toResponse(clinic);
    }

    private Clinic findOrThrow(UUID id) {
        return clinicRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Clinic", id));
    }

    private ClinicResponse toResponse(Clinic c) {
        return new ClinicResponse(
                c.getId(), c.getName(), c.getCui(), c.getStatus(),
                c.getStripeAccountId(), c.getCommissionRate(), c.getCreatedAt());
    }
}
