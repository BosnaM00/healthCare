package org.example.healthcare.service;

import org.example.healthcare.dto.medic.MedicRequest;
import org.example.healthcare.dto.medic.MedicResponse;
import org.example.healthcare.dto.medic.MedicSearchRequest;
import org.example.healthcare.dto.medic.MedicVerificationRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface MedicService {

    MedicResponse createProfile(UUID userId, MedicRequest request);

    MedicResponse getById(UUID id);

    MedicResponse getByUserId(UUID userId);

    MedicResponse updateProfile(UUID userId, MedicRequest request);

    MedicResponse updateSpecialties(UUID medicId, List<UUID> specialtyIds);

    Page<MedicResponse> search(MedicSearchRequest request, Pageable pageable);

    MedicResponse updateVerification(UUID id, MedicVerificationRequest request);
}
