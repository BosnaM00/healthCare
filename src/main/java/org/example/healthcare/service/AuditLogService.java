package org.example.healthcare.service;

import org.example.healthcare.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AuditLogService {

    /**
     * Appends a new audit record. Never updates or deletes existing records.
     *
     * @param actorId    user who triggered the event; null for system actions
     * @param action     event verb, e.g. "PAYMENT_RELEASED"
     * @param entityType simple class name, e.g. "Payment"
     * @param entityId   primary key of the affected row
     * @param oldValue   JSON snapshot before change; null for CREATE events
     * @param newValue   JSON snapshot after change; null for DELETE events
     * @param ipAddress  originating IP; null when not applicable
     */
    void log(UUID actorId, String action, String entityType, UUID entityId,
             String oldValue, String newValue, String ipAddress);

    Page<AuditLog> getByActor(UUID actorId, Pageable pageable);

    Page<AuditLog> getByEntity(String entityType, UUID entityId, Pageable pageable);
}
