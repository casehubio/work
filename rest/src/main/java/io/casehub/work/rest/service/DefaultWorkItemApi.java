package io.casehub.work.rest.service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.platform.api.path.Path;
import io.casehub.work.api.WorkItem;
import io.casehub.work.api.WorkItemCreateRequest;
import io.casehub.work.api.WorkItemLifecycleEvent;
import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.api.WorkItemQuery;
import io.casehub.work.api.WorkItemRootView;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.api.WorkItemSummary;
import io.casehub.work.api.spi.WorkItemApi;
import io.casehub.work.api.spi.WorkItemOperations;
import io.casehub.work.api.spi.WorkItemStore;
import io.casehub.work.api.view.WorkItemPage;
import io.casehub.work.api.view.WorkItemView;
import io.casehub.work.api.view.WorkItemWithAuditView;
import io.casehub.work.runtime.event.WorkItemEventBroadcaster;
import io.casehub.work.runtime.model.AuditEntry;
import io.casehub.work.runtime.repository.AuditEntryStore;
import io.casehub.work.runtime.service.WorkItemNotFoundException;
import io.smallrye.mutiny.Multi;

@ApplicationScoped
public class DefaultWorkItemApi implements WorkItemApi {

    @Inject
    WorkItemOperations workItemOperations;

    @Inject
    WorkItemStore workItemStore;

    @Inject
    AuditEntryStore auditEntryStore;

    @Inject
    WorkItemEventBroadcaster broadcaster;

    @Override
    public WorkItemPage listAll(WorkItemStatus status, WorkItemPriority priority,
                                String label, String outcome,
                                String tenancyId, Integer offset, Integer limit) {
        WorkItemQuery.Builder qb = WorkItemQuery.builder();
        if (status != null) qb.status(status);
        if (priority != null) qb.priority(priority);
        if (label != null) qb.labelPattern(label);

        List<WorkItem> results = workItemStore.scan(qb.build());

        Stream<WorkItem> stream = results.stream();
        if (outcome != null) {
            stream = stream.filter(wi -> outcome.equals(wi.outcome()));
        }

        List<WorkItemView> allItems = stream.map(ViewMapper::toView).toList();

        int off = offset != null ? offset : 0;
        int lim = limit != null ? limit : allItems.size();
        List<WorkItemView> page = allItems.subList(
                Math.min(off, allItems.size()),
                Math.min(off + lim, allItems.size()));
        boolean hasMore = off + lim < allItems.size();

        return new WorkItemPage(page, allItems.size(), hasMore);
    }

    @Override
    public WorkItemWithAuditView getById(UUID workItemId, String tenancyId) {
        WorkItem wi = workItemOperations.findById(workItemId)
                .orElseThrow(() -> new WorkItemNotFoundException(workItemId));
        List<AuditEntry> trail = auditEntryStore.findByWorkItemId(workItemId);
        return ViewMapper.toWithAuditView(wi, trail);
    }

    @Override
    public WorkItemView create(WorkItemCreateRequest request, String tenancyId) {
        return ViewMapper.toView(workItemOperations.create(request));
    }

    @Override
    public WorkItemView clone(UUID workItemId, String title, String createdBy, String tenancyId) {
        String effectiveCreatedBy = createdBy != null ? createdBy : "unknown";
        return ViewMapper.toView(workItemOperations.clone(workItemId, title, effectiveCreatedBy));
    }

    @Override
    public WorkItemSummary inboxSummary(String assignee, List<String> candidateGroups,
                                        String candidateUser, WorkItemStatus status,
                                        WorkItemPriority priority, String type,
                                        String tenancyId) {
        WorkItemQuery.Builder qb = WorkItemQuery.inbox(assignee, candidateGroups, candidateUser).toBuilder();
        if (status != null) qb.status(status);
        if (priority != null) qb.priority(priority);
        if (type != null) qb.type(type);
        return workItemStore.summaryByQuery(qb.build(), Instant.now());
    }

    @Override
    public List<WorkItemRootView> inbox(String assignee, List<String> candidateGroups,
                                        String candidateUser, WorkItemStatus status,
                                        WorkItemPriority priority, String type,
                                        Boolean followUp, String outcome, String tenancyId) {
        Stream<WorkItemRootView> stream = workItemStore
                .scanRoots(assignee, candidateUser, candidateGroups).stream();
        if (status != null) stream = stream.filter(v -> Objects.equals(v.workItem().status(), status));
        if (priority != null) stream = stream.filter(v -> Objects.equals(v.workItem().priority(), priority));
        if (type != null) {
            Path queryPath = Path.parse(type);
            stream = stream.filter(v -> v.workItem().types().stream().anyMatch(t -> {
                Path typePath = Path.parse(t);
                return typePath.equals(queryPath) || queryPath.isAncestorOf(typePath);
            }));
        }
        if (followUp != null) {
            stream = stream.filter(v -> Boolean.TRUE.equals(followUp)
                    ? v.workItem().followUpDate() != null
                    : v.workItem().followUpDate() == null);
        }
        if (outcome != null) stream = stream.filter(v -> outcome.equals(v.workItem().outcome()));
        return stream.toList();
    }

    @Override
    public WorkItemView addLabel(UUID workItemId, String path, String appliedBy, String tenancyId) {
        String effectiveAppliedBy = appliedBy != null ? appliedBy : "unknown";
        return ViewMapper.toView(workItemOperations.addLabel(workItemId, path, effectiveAppliedBy));
    }

    @Override
    public WorkItemView removeLabel(UUID workItemId, String path, String tenancyId) {
        return ViewMapper.toView(workItemOperations.removeLabel(workItemId, path));
    }

    @Override
    public Multi<WorkItemLifecycleEvent> streamEvents(UUID workItemId, String type, String tenancyId) {
        return broadcaster.stream(workItemId, type, tenancyId);
    }

    @Override
    public Multi<WorkItemLifecycleEvent> streamWorkItemEvents(UUID workItemId, String tenancyId) {
        return broadcaster.stream(workItemId, null, tenancyId);
    }
}
