package io.casehub.work.progress.runtime.repository;

import io.casehub.work.progress.ProgressChangeType;
import io.casehub.work.progress.ProgressStatus;
import io.casehub.work.progress.ProgressUpdatedEvent;
import io.casehub.work.progress.spi.ProgressEventStore;
import io.casehub.work.progress.runtime.model.ProgressEventEntity;
import io.casehub.work.runtime.repository.jpa.TenantAwareStore;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class JpaProgressEventStore extends TenantAwareStore implements ProgressEventStore {

    @Override
    public void append(ProgressUpdatedEvent event) {
        withTenantRun(() -> {
            ProgressEventEntity entity = toEntity(event);
            entity.persistAndFlush();
        });
    }

    @Override
    public List<ProgressUpdatedEvent> findByProgressId(UUID progressId) {
        return withTenantQuery(() ->
                ProgressEventEntity.<ProgressEventEntity>find(
                                "progressId = ?1 ORDER BY occurredAt ASC", progressId)
                        .list()
                        .stream()
                        .map(this::toDomain)
                        .toList());
    }

    @Override
    public List<ProgressUpdatedEvent> findByProgressIdSince(UUID progressId, Instant since) {
        return withTenantQuery(() ->
                ProgressEventEntity.<ProgressEventEntity>find(
                                "progressId = ?1 AND occurredAt > ?2 ORDER BY occurredAt ASC",
                                progressId, since)
                        .list()
                        .stream()
                        .map(this::toDomain)
                        .toList());
    }

    @Override
    public List<ProgressUpdatedEvent> findByRootProgressIdSince(UUID rootProgressId, Instant since) {
        return withTenantQuery(() ->
                ProgressEventEntity.<ProgressEventEntity>find(
                                "rootProgressId = ?1 AND occurredAt > ?2 ORDER BY occurredAt ASC",
                                rootProgressId, since)
                        .list()
                        .stream()
                        .map(this::toDomain)
                        .toList());
    }

    private ProgressEventEntity toEntity(ProgressUpdatedEvent event) {
        ProgressEventEntity entity = new ProgressEventEntity();
        entity.id = UUID.randomUUID();
        entity.tenancyId = event.tenancyId();
        entity.progressId = event.progressId();
        entity.rootProgressId = event.rootProgressId();
        entity.scopeType = event.scopeType();
        entity.scopeId = event.scopeId();
        entity.changeType = event.changeType().name();
        entity.previousState = event.previousState();
        entity.currentState = event.currentState();
        entity.status = event.status().name();
        entity.occurredAt = event.timestamp();
        return entity;
    }

    private ProgressUpdatedEvent toDomain(ProgressEventEntity entity) {
        return new ProgressUpdatedEvent(
                entity.progressId,
                entity.tenancyId,
                entity.scopeType,
                entity.scopeId,
                null,
                entity.rootProgressId,
                null,
                entity.previousState,
                entity.currentState,
                ProgressStatus.valueOf(entity.status),
                ProgressChangeType.valueOf(entity.changeType),
                entity.occurredAt
        );
    }
}
