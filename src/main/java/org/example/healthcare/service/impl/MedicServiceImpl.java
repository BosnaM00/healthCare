package org.example.healthcare.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.healthcare.common.exception.BusinessException;
import org.example.healthcare.common.exception.ResourceNotFoundException;
import org.example.healthcare.dto.medic.MedicRequest;
import org.example.healthcare.dto.medic.MedicResponse;
import org.example.healthcare.dto.medic.MedicSearchRequest;
import org.example.healthcare.dto.medic.MedicVerificationRequest;
import org.example.healthcare.dto.specialty.SpecialtyResponse;
import org.example.healthcare.model.Clinic;
import org.example.healthcare.model.Medic;
import org.example.healthcare.model.Specialty;
import org.example.healthcare.model.User;
import org.example.healthcare.repository.ClinicRepository;
import org.example.healthcare.repository.MedicRepository;
import org.example.healthcare.repository.SpecialtyRepository;
import org.example.healthcare.repository.UserRepository;
import org.example.healthcare.service.MedicService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MedicServiceImpl implements MedicService {

    private final MedicRepository medicRepository;
    private final UserRepository userRepository;
    private final SpecialtyRepository specialtyRepository;
    private final ClinicRepository clinicRepository;

    @Override
    @Transactional
    public MedicResponse createProfile(UUID userId, MedicRequest request) {
        if (medicRepository.findByUserId(userId).isPresent())
            throw new BusinessException("Medic profile already exists for this user");
        if (medicRepository.findByLicenseNumber(request.licenseNumber()).isPresent())
            throw new BusinessException("License number already registered");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        Medic medic = Medic.builder()
                .user(user)
                .clinic(resolveClinic(request.clinicId()))
                .licenseNumber(request.licenseNumber())
                .licenseExpiresAt(request.licenseExpiresAt())
                .availableForInstant(request.availableForInstant())
                .build();

        attachSpecialties(medic, request.specialtyIds());
        return toResponse(medicRepository.save(medic));
    }

    @Override
    public MedicResponse getById(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Override
    public MedicResponse getByUserId(UUID userId) {
        return toResponse(medicRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Medic profile not found for user: " + userId)));
    }

    @Override
    @Transactional
    public MedicResponse updateProfile(UUID userId, MedicRequest request) {
        Medic medic = medicRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Medic profile not found for user: " + userId));
        medic.setClinic(resolveClinic(request.clinicId()));
        medic.setLicenseNumber(request.licenseNumber());
        medic.setLicenseExpiresAt(request.licenseExpiresAt());
        medic.setAvailableForInstant(request.availableForInstant());
        attachSpecialties(medic, request.specialtyIds());
        return toResponse(medic);
    }

    @Override
    @Transactional
    public MedicResponse updateSpecialties(UUID medicId, List<UUID> specialtyIds) {
        Medic medic = findOrThrow(medicId);
        medic.getMedicSpecialties().clear();
        List<Specialty> specialties = specialtyRepository.findAllById(specialtyIds);
        if (specialties.size() != specialtyIds.size())
            throw new BusinessException("One or more specialty IDs not found");
        specialties.forEach(medic::addSpecialty);
        return toResponse(medic);
    }

    @Override
    public Page<MedicResponse> search(MedicSearchRequest request, Pageable pageable) {
        Specification<Medic> spec = Specification.where(MedicSpecifications.verificationActive());
        if (request.specialtyId() != null)
            spec = spec.and(MedicSpecifications.hasSpecialty(request.specialtyId()));
        if (request.clinicId() != null)
            spec = spec.and(MedicSpecifications.hasClinic(request.clinicId()));
        if (Boolean.TRUE.equals(request.instantOnly()))
            spec = spec.and(MedicSpecifications.isInstant());
        return medicRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Override
    @Transactional
    public MedicResponse updateVerification(UUID id, MedicVerificationRequest request) {
        Medic medic = findOrThrow(id);
        medic.setVerificationStatus(request.status());
        return toResponse(medic);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Returns null for independent practitioners; throws if the ID is non-null but not found. */
    private Clinic resolveClinic(UUID clinicId) {
        if (clinicId == null) return null;
        return clinicRepository.findById(clinicId)
                .orElseThrow(() -> new ResourceNotFoundException("Clinic", clinicId));
    }

    private void attachSpecialties(Medic medic, List<UUID> specialtyIds) {
        medic.getMedicSpecialties().clear();
        List<Specialty> specialties = specialtyRepository.findAllById(specialtyIds);
        if (specialties.size() != specialtyIds.size())
            throw new BusinessException("One or more specialty IDs not found");
        specialties.forEach(medic::addSpecialty);
    }

    private Medic findOrThrow(UUID id) {
        return medicRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Medic", id));
    }

    private MedicResponse toResponse(Medic medic) {
        List<SpecialtyResponse> specialties = medic.getMedicSpecialties().stream()
                .map(ms -> new SpecialtyResponse(
                        ms.getSpecialty().getId(),
                        ms.getSpecialty().getName(),
                        ms.getSpecialty().getDescription()))
                .toList();
        return new MedicResponse(
                medic.getId(),
                medic.getUser().getId(),
                medic.getClinic() != null ? medic.getClinic().getId() : null,
                medic.getLicenseNumber(),
                medic.getLicenseExpiresAt(),
                medic.getVerificationStatus(),
                medic.isAvailableForInstant(),
                specialties
        );
    }
}
