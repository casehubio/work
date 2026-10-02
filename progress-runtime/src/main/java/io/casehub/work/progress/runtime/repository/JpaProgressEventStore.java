package io.casehub.work.progress.runtime.repository;

import io.casehub.work.progress.ProgressChangeType;
import io.casehub.work.progress.ProgressStatus;
import io.casehub.work.progress.ProgressUpdatedEvent;
import io.casehub.work.progress.runtime.model.ProgressEventEntity;
import io.casehub.work.progress.spi.ProgressEventStore;
import io.casehub.work.runtime.repository.jpa.TenantAwareStore;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class JpaProgressEventStore extends TenantAwareStore implements ProgressEventStore {

    @Override
    public void append(ProgressUpdatedEvent event) {
        withTenantRun(() -> {
            ProgressEventEntity entity = toEntity(event);
            em.persist(entity);
            em.flush();
        });
    }

    @Override
    public Optional<ProgressUpdatedEvent> findById(UUID eventId) {
        return withTenantQuery(() ->
                                       Optional.ofNullable(em.find(ProgressEventEntity.class, eventId))
                        .map(this::toDomain));
    }

    @Override
    public Optional<ProgressUpdatedEvent> findLastEventAtOrBefore(UUID progressId, Instant cutoff) {
        return em.createQuery("FROM ProgressEventEntity WHERE progressId = ?1 AND occurredAt <= ?2 ORDER BY occurredAt DESC, id DESC", ProgressEventEntity.class)
                .setParameter(1, progressId)
                .setParameter(2, cutoff)
                .getResultStream().findFirst()
                .map(this::toDomain);
    }


    @Override
    public List<ProgressUpdatedEvent> findByProgressId(UUID progressId) {
        return withTenantQuery(() ->
                em.createQuery("FROM ProgressEventEntity WHERE progressId = ?1 ORDER BY occurredAt ASC", ProgressEventEntity.class)
                        .setParameter(1, progressId)
                        .getResultList()
                        .stream()
                        .map(this::toDomain)
                        .toList());
    }

    @Override
    public List<ProgressUpdatedEvent> findByProgressIdSince(UUID progressId, Instant since) {
        return withTenantQuery(() ->
                em.createQuery("FROM ProgressEventEntity WHERE progressId = ?1 AND occurredAt > ?2 ORDER BY occurredAt ASC", ProgressEventEntity.class)
                        .setParameter(1, progressId)
                        .setParameter(2, since)
                        .getResultList()
                        .stream()
                        .map(this::toDomain)
                        .toList());
    }

    @Override
    public List<ProgressUpdatedEvent> findByRootProgressIdSince(UUID rootProgressId, Instant since) {
        return withTenantQuery(() ->
                em.createQuery("FROM ProgressEventEntity WHERE rootProgressId = ?1 AND occurredAt > ?2 ORDER BY occurredAt ASC", ProgressEventEntity.class)
                        .setParameter(1, rootProgressId)
                        .setParameter(2, since)
                        .getResultList()
                        .stream()
                        .map(this::toDomain)
                        .toList());
    }

    private ProgressEventEntity toEntity(ProgressUpdatedEvent event) {
        ProgressEventEntity entity = new ProgressEventEntity();
        entity.id             = event.id();
        entity.tenancyId      = event.tenancyId();
        entity.progressId     = event.progressId();
        entity.rootProgressId = event.rootProgressId();
        entity.scopeType      = event.scopeType();
        entity.scopeId        = event.scopeId();
        entity.changeType     = event.changeType().name();
        entity.previousState  = event.previousState();
        entity.currentState   = event.currentState();
        entity.status         = event.status().name();
        entity.occurredAt     = event.timestamp();
        entity.operationId    = event.operationId();
        return entity;
    }

    private ProgressUpdatedEvent toDomain(ProgressEventEntity entity) {
        return new ProgressUpdatedEvent(
                entity.id,
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
                entity.occurredAt,
                entity.operationId);
    }
}
