package io.casehub.work.rest.service;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.casehub.work.api.WorkItem;
import io.casehub.work.api.WorkItemRelationType;
import io.casehub.work.api.spi.WorkItemOperations;
import io.casehub.work.api.spi.WorkItemRelationApi;
import io.casehub.work.api.spi.WorkItemStore;
import io.casehub.work.api.view.AddRelationRequest;
import io.casehub.work.api.view.WorkItemRelationView;
import io.casehub.work.api.view.WorkItemView;
import io.casehub.work.runtime.model.WorkItemRelation;
import io.casehub.work.runtime.repository.WorkItemRelationStore;

@ApplicationScoped
public class DefaultWorkItemRelationApi implements WorkItemRelationApi {

    @Inject
    WorkItemRelationStore relationStore;

    @Inject
    WorkItemStore workItemStore;

    @Inject
    WorkItemOperations workItemOperations;

    @Override
    @Transactional
    public WorkItemRelationView addRelation(UUID workItemId, AddRelationRequest body, String tenancyId) {
        UUID targetId = body.targetId();
        String relationType = body.relationType();

        if (workItemId.equals(targetId)) {
            throw new IllegalArgumentException("Cannot create self-referencing relation");
        }

        relationStore.findExisting(workItemId, targetId, relationType).ifPresent(existing -> {
            throw new IllegalArgumentException("Relation already exists: " + existing.id);
        });

        if (WorkItemRelationType.PART_OF.equals(relationType)) {
            detectCycle(workItemId, targetId);
        }

        WorkItemRelation rel = new WorkItemRelation();
        rel.sourceId = workItemId;
        rel.targetId = targetId;
        rel.relationType = relationType;
        rel.createdBy = "system";
        rel.tenancyId = tenancyId;
        return ViewMapper.toRelationView(relationStore.put(rel));
    }

    @Override
    public List<WorkItemRelationView> listOutgoing(UUID workItemId, String tenancyId) {
        return relationStore.findBySourceId(workItemId).stream()
                .map(ViewMapper::toRelationView)
                .toList();
    }

    @Override
    public List<WorkItemRelationView> listIncoming(UUID workItemId, String tenancyId) {
        return relationStore.findByTargetId(workItemId).stream()
                .map(ViewMapper::toRelationView)
                .toList();
    }

    @Override
    @Transactional
    public void deleteRelation(UUID workItemId, UUID relationId, String tenancyId) {
        relationStore.delete(relationId);
    }

    @Override
    public List<WorkItemView> children(UUID workItemId, String tenancyId) {
        return relationStore.findByTargetAndType(workItemId, WorkItemRelationType.PART_OF).stream()
                .map(rel -> workItemOperations.findById(rel.sourceId).orElse(null))
                .filter(wi -> wi != null)
                .map(ViewMapper::toView)
                .toList();
    }

    @Override
    public WorkItemView parent(UUID workItemId, String tenancyId) {
        return relationStore.findBySourceAndType(workItemId, WorkItemRelationType.PART_OF).stream()
                .findFirst()
                .flatMap(rel -> workItemOperations.findById(rel.targetId))
                .map(ViewMapper::toView)
                .orElse(null);
    }

    private void detectCycle(UUID sourceId, UUID targetId) {
        Set<UUID> visited = new HashSet<>();
        Queue<UUID> queue = new ArrayDeque<>();
        queue.add(targetId);
        visited.add(targetId);

        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            List<WorkItemRelation> outgoing = relationStore.findBySourceAndType(current, WorkItemRelationType.PART_OF);
            for (WorkItemRelation rel : outgoing) {
                if (rel.targetId.equals(sourceId)) {
                    throw new IllegalArgumentException(
                            "Adding this relation would create a cycle in the PART_OF hierarchy");
                }
                if (visited.add(rel.targetId)) {
                    queue.add(rel.targetId);
                }
            }
        }
    }
}
