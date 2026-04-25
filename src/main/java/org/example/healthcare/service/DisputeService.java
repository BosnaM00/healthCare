package org.example.healthcare.service;

import org.example.healthcare.dto.dispute.DisputeRequest;
import org.example.healthcare.dto.dispute.DisputeResolutionRequest;
import org.example.healthcare.dto.dispute.DisputeResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface DisputeService {

    /** Patient raises a dispute against a completed consultation */
    DisputeResponse open(UUID patientUserId, DisputeRequest request);

    /** Admin claims ownership — transitions OPEN → UNDER_REVIEW */
    DisputeResponse claimForReview(UUID disputeId, UUID adminUserId);

    /** Admin resolves the dispute; triggers payment action based on resolution */
    DisputeResponse resolve(UUID disputeId, UUID adminUserId, DisputeResolutionRequest request);

    DisputeResponse getById(UUID id);

    Page<DisputeResponse> listByStatus(String status, Pageable pageable);
}
