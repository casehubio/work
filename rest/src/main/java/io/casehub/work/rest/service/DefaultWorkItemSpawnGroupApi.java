package io.casehub.work.rest.service;

import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.work.api.WorkItemRelationType;
import io.casehub.work.api.spi.WorkItemSpawnGroupApi;
import io.casehub.work.api.view.SpawnGroupChildView;
import io.casehub.work.api.view.SpawnGroupView;
import io.casehub.work.runtime.model.WorkItemSpawnGroup;
import io.casehub.work.runtime.repository.WorkItemRelationStore;
import io.casehub.work.runtime.repository.WorkItemSpawnGroupStore;

@ApplicationScoped
public class DefaultWorkItemSpawnGroupApi implements WorkItemSpawnGroupApi {

    @Inject
    WorkItemSpawnGroupStore spawnGroupStore;

    @Inject
    WorkItemRelationStore relationStore;

    @Override
    public SpawnGroupView getGroup(UUID groupId, String tenancyId) {
        final WorkItemSpawnGroup group = spawnGroupStore.get(groupId).orElse(null);
        if (group == null) {
            return null;
        }
        final String createdByMarker = "system:spawn:" + groupId;
        final List<SpawnGroupChildView> children = relationStore
                .findByTargetAndType(group.parentId, WorkItemRelationType.PART_OF)
                .stream()
                .filter(r -> createdByMarker.equals(r.createdBy))
                .map(r -> new SpawnGroupChildView(r.sourceId, r.createdAt))
                .toList();
        return new SpawnGroupView(group.id, group.parentId, group.idempotencyKey,
                group.createdAt, children);
    }
}
