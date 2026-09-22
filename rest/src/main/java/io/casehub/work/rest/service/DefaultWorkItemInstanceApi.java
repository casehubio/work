package io.casehub.work.rest.service;

import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.work.api.GroupStatus;
import io.casehub.work.api.WorkItem;
import io.casehub.work.api.spi.WorkItemInstanceApi;
import io.casehub.work.api.spi.WorkItemStore;
import io.casehub.work.api.view.InstancesView;
import io.casehub.work.runtime.model.WorkItemSpawnGroup;
import io.casehub.work.runtime.repository.WorkItemSpawnGroupStore;

@ApplicationScoped
public class DefaultWorkItemInstanceApi implements WorkItemInstanceApi {

    @Inject
    WorkItemStore workItemStore;

    @Inject
    WorkItemSpawnGroupStore spawnGroupStore;

    @Override
    public InstancesView getInstances(UUID workItemId, String tenancyId) {
        final WorkItem parent = workItemStore.get(workItemId).orElse(null);
        if (parent == null) {
            return null;
        }

        final WorkItemSpawnGroup group = spawnGroupStore.findMultiInstanceByParentId(workItemId).orElse(null);
        if (group == null) {
            return null;
        }

        final List<WorkItem> children = workItemStore.findByParentId(workItemId);
        final GroupStatus status = group.groupStatus != null ? group.groupStatus : GroupStatus.IN_PROGRESS;

        return new InstancesView(
                workItemId,
                group.id,
                group.instanceCount,
                group.requiredCount,
                group.completedCount,
                group.rejectedCount,
                status.name(),
                children.stream().map(ViewMapper::toView).toList());
    }
}
