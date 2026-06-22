package org.example.healthcare.dto.medic;

import org.example.healthcare.dto.specialty.SpecialtyResponse;
import org.example.healthcare.model.ConsultationType;
import org.example.healthcare.model.VerificationStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record MedicResponse(
        UUID id,
        UUID userId,
        UUID clinicId,
        String clinicName,
        String firstName,
        String lastName,
        String licenseNumber,
        LocalDate licenseExpiresAt,
        VerificationStatus verificationStatus,
        boolean availableForInstant,
        List<SpecialtyResponse> specialties,
        List<ConsultationType> consultationTypes,
        List<String> languages,
        BigDecimal pricePerSession,
        String currency
) {}
