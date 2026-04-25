package org.example.healthcare.service;

import org.example.healthcare.dto.clinic.ClinicRequest;
import org.example.healthcare.dto.clinic.ClinicResponse;
import org.example.healthcare.dto.clinic.ClinicStatusRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ClinicService {

    ClinicResponse create(ClinicRequest request);

    ClinicResponse getById(UUID id);

    Page<ClinicResponse> listAll(Pageable pageable);

    ClinicResponse update(UUID id, ClinicRequest request);

    ClinicResponse updateStatus(UUID id, ClinicStatusRequest request);

    /** Link a Stripe Connect account after the clinic completes Stripe onboarding */
    ClinicResponse setStripeAccount(UUID id, String stripeAccountId);
}
