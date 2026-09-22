package io.casehub.work.rest.service;

import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.work.api.ChildSpec;
import io.casehub.work.api.SpawnRequest;
import io.casehub.work.api.SpawnResult;
import io.casehub.work.api.spi.WorkItemSpawnApi;
import io.casehub.work.api.view.SpawnBodyRequest;
import io.casehub.work.api.view.SpawnGroupSummaryView;
import io.casehub.work.api.view.SpawnResultView;
import io.casehub.work.api.view.SpawnedChildView;
import io.casehub.work.runtime.model.WorkItemSpawnGroup;
import io.casehub.work.runtime.repository.WorkItemSpawnGroupStore;
import io.casehub.work.runtime.service.WorkItemNotFoundException;
import io.casehub.work.runtime.service.WorkItemSpawnService;

@ApplicationScoped
public class DefaultWorkItemSpawnApi implements WorkItemSpawnApi {

    @Inject
    WorkItemSpawnService spawnService;

    @Inject
    WorkItemSpawnGroupStore spawnGroupStore;

    @Override
    public SpawnResultView spawn(UUID workItemId, SpawnBodyRequest body, String tenancyId) {
        if (body == null) {
            throw new IllegalArgumentException("request body is required");
        }
        if (body.children() == null || body.children().isEmpty()) {
            throw new IllegalArgumentException("children must not be empty");
        }
        if (body.idempotencyKey() == null || body.idempotencyKey().isBlank()) {
            throw new IllegalArgumentException("idempotencyKey is required");
        }
        for (final var child : body.children()) {
            if (child.templateId() == null) {
                throw new IllegalArgumentException("templateId is required");
            }
        }

        final List<ChildSpec> specs = body.children().stream()
                .map(c -> new ChildSpec(UUID.fromString(c.templateId()), c.callerRef(), c.overrides()))
                .toList();
        final SpawnRequest request = new SpawnRequest(workItemId, body.idempotencyKey(), specs);

        try {
            final SpawnResult result = spawnService.spawn(request);
            return new SpawnResultView(
                    result.groupId(),
                    result.children().stream()
                            .map(c -> new SpawnedChildView(c.workItemId(),
                                    c.callerRef() != null ? c.callerRef() : ""))
                            .toList(),
                    result.created());
        } catch (WorkItemNotFoundException e) {
            return null;
        } catch (IllegalStateException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    @Override
    public List<SpawnGroupSummaryView> listSpawnGroups(UUID workItemId, String tenancyId) {
        return spawnGroupStore.findByParentId(workItemId).stream()
                .map(g -> new SpawnGroupSummaryView(g.id, g.parentId, g.idempotencyKey, g.createdAt))
                .toList();
    }

    @Override
    public void cancelGroup(UUID workItemId, UUID groupId, boolean cancelChildren, String tenancyId) {
        final WorkItemSpawnGroup group = spawnGroupStore.get(groupId).orElse(null);
        if (group == null || !group.parentId.equals(workItemId)) {
            throw new IllegalArgumentException("Spawn group not found");
        }
        try {
            spawnService.cancelGroup(groupId, cancelChildren);
        } catch (WorkItemNotFoundException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }
}
