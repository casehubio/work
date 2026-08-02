package io.casehub.work.progress.runtime.repository;

import io.casehub.work.progress.ProgressInstance;
import io.casehub.work.progress.spi.ProgressInstanceStore;
import io.casehub.work.progress.runtime.model.ProgressInstanceEntity;
import io.casehub.work.runtime.repository.jpa.TenantAwareStore;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class JpaProgressInstanceStore extends TenantAwareStore implements ProgressInstanceStore {

    @Override
    public ProgressInstance put(ProgressInstance instance) {
        return withTenantQuery(() -> {
            ProgressInstanceEntity existing = em.find(ProgressInstanceEntity.class, instance.id());
            if (existing != null) {
                ProgressInstanceMapper.updateEntity(existing, instance);
                existing.persistAndFlush();
                return ProgressInstanceMapper.toDomain(existing);
            }
            ProgressInstanceEntity entity = ProgressInstanceMapper.toEntity(instance);
            if (entity.tenancyId == null) {
                entity.tenancyId = currentPrincipal.tenancyId();
            }
            entity.persistAndFlush();
            return ProgressInstanceMapper.toDomain(entity);
        });
    }

    @Override
    public Optional<ProgressInstance> get(UUID id) {
        return withTenantQuery(() -> {
            ProgressInstanceEntity entity = em.find(ProgressInstanceEntity.class, id);
            return entity != null ? Optional.of(ProgressInstanceMapper.toDomain(entity)) : Optional.empty();
        });
    }

    @Override
    public List<ProgressInstance> findByScopeTypeAndScopeId(String scopeType, String scopeId) {
        return withTenantQuery(() ->
                ProgressInstanceEntity.<ProgressInstanceEntity>find(
                                "scopeType = ?1 AND scopeId = ?2 ORDER BY createdAt DESC",
                                scopeType, scopeId)
                        .list()
                        .stream()
                        .map(ProgressInstanceMapper::toDomain)
                        .toList());
    }

    @Override
    public List<ProgressInstance> findByParentProgressId(UUID parentProgressId) {
        return withTenantQuery(() ->
                ProgressInstanceEntity.<ProgressInstanceEntity>find(
                                "parentProgressId = ?1", parentProgressId)
                        .list()
                        .stream()
                        .map(ProgressInstanceMapper::toDomain)
                        .toList());
    }
}
