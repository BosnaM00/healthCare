package org.example.healthcare.repository;

import org.example.healthcare.model.Dispute;
import org.example.healthcare.model.DisputeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DisputeRepository extends JpaRepository<Dispute, UUID> {

    List<Dispute> findByConsultationId(UUID consultationId);

    boolean existsByConsultationIdAndStatusIn(UUID consultationId, List<DisputeStatus> statuses);

    Page<Dispute> findByStatus(DisputeStatus status, Pageable pageable);

    Page<Dispute> findByRaisedById(UUID raisedById, Pageable pageable);
}
