package io.casehub.work.progress.runtime.repository;

import io.casehub.work.progress.ProgressInstance;
import io.casehub.work.progress.runtime.model.ProgressInstanceEntity;

public final class ProgressInstanceMapper {

    private ProgressInstanceMapper() {}

    public static ProgressInstance toDomain(ProgressInstanceEntity entity) {
        return new ProgressInstance(
                entity.id,
                entity.tenancyId,
                entity.scopeType,
                entity.scopeId,
                entity.parentProgressId,
                entity.rootProgressId,
                entity.shapeType,
                entity.definition,
                entity.state,
                entity.status,
                entity.rollupStrategyId,
                entity.createdAt,
                entity.updatedAt
        );
    }

    public static ProgressInstanceEntity toEntity(ProgressInstance instance) {
        ProgressInstanceEntity entity = new ProgressInstanceEntity();
        entity.id = instance.id();
        entity.tenancyId = instance.tenancyId();
        entity.scopeType = instance.scopeType();
        entity.scopeId = instance.scopeId();
        entity.parentProgressId = instance.parentProgressId();
        entity.rootProgressId = instance.rootProgressId();
        entity.shapeType = instance.shapeType();
        entity.definition = instance.definition();
        entity.state = instance.state();
        entity.status = instance.status();
        entity.rollupStrategyId = instance.rollupStrategyId();
        entity.createdAt = instance.createdAt();
        entity.updatedAt = instance.updatedAt();
        return entity;
    }

    public static void updateEntity(ProgressInstanceEntity entity, ProgressInstance instance) {
        entity.state = instance.state();
        entity.status = instance.status();
        entity.updatedAt = instance.updatedAt();
    }
}
