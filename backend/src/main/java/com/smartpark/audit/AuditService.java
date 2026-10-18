package com.smartpark.audit;

import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;
import java.util.UUID;

/**
 * Best-effort audit trail. Events are persisted <em>after</em> the calling
 * transaction commits, so audit writes can see newly created rows and can
 * never roll back (or be rolled back by) business work.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditWriter auditWriter;

    public void log(UUID actorUserId, String action, String entityType, String entityId,
                    Map<String, Object> metadata) {
        AuditLog entry = new AuditLog();
        entry.setActorUserId(actorUserId);
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setMetadata(metadata == null ? Map.of() : metadata);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    write(entry);
                }
            });
        } else {
            write(entry);
        }
    }

    private void write(AuditLog entry) {
        try {
            auditWriter.write(entry);
        } catch (Exception ex) {
            LoggerFactory.getLogger(AuditService.class)
                    .warn("Audit write failed for {} {}", entry.getEntityType(), entry.getEntityId(), ex);
        }
    }
}
