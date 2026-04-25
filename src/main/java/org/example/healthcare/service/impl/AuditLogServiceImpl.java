package org.example.healthcare.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.healthcare.model.AuditLog;
import org.example.healthcare.repository.AuditLogRepository;
import org.example.healthcare.service.AuditLogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Uses REQUIRES_NEW so audit logs are committed even if the outer transaction rolls back.
     * This guarantees a record of attempted — not just successful — state changes.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(UUID actorId, String action, String entityType, UUID entityId,
                    String oldValue, String newValue, String ipAddress) {
        AuditLog entry = AuditLog.builder()
                .actorId(actorId)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .oldValue(oldValue)
                .newValue(newValue)
                .ipAddress(ipAddress)
                .build();
        auditLogRepository.save(entry);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditLog> getByActor(UUID actorId, Pageable pageable) {
        return auditLogRepository.findByActorId(actorId, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditLog> getByEntity(String entityType, UUID entityId, Pageable pageable) {
        return auditLogRepository.findByEntityTypeAndEntityId(entityType, entityId, pageable);
    }
}
