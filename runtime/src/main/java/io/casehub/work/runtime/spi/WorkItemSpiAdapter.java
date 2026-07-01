package io.casehub.work.runtime.spi;

import java.util.Optional;
import java.util.UUID;

import io.casehub.work.api.WorkItemCreateRequest;
import io.casehub.work.api.WorkItemRef;
import io.casehub.work.api.spi.WorkItemCreator;
import io.casehub.work.api.spi.WorkItemLifecycle;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.service.WorkItemService;
import io.casehub.work.runtime.service.WorkItemTemplateService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Hexagonal adapter implementing the SPI interfaces from {@code casehub-work-api}.
 *
 * <p>
 * Routes template-based creation to {@link WorkItemTemplateService#createFromTemplate} and
 * direct creation to {@link WorkItemService#create}. Lifecycle operations delegate to
 * {@link WorkItemService} with idempotent semantics for terminal states — if the WorkItem
 * has already reached a terminal status, cancel/complete are no-ops instead of throwing.
 */
@ApplicationScoped
public class WorkItemSpiAdapter implements WorkItemCreator, WorkItemLifecycle {

    private final WorkItemService workItemService;
    private final WorkItemTemplateService workItemTemplateService;

    @Inject
    public WorkItemSpiAdapter(final WorkItemService workItemService,
                              final WorkItemTemplateService workItemTemplateService) {
        this.workItemService = workItemService;
        this.workItemTemplateService = workItemTemplateService;
    }

    @Override
    public WorkItemRef create(final WorkItemCreateRequest request) {
        final WorkItem item;
        if (request.templateId != null) {
            item = workItemTemplateService.createFromTemplate(request);
        } else {
            item = workItemService.create(request);
        }
        return toRef(item);
    }

    @Override
    public Optional<WorkItemRef> findByCallerRef(final String callerRef) {
        return workItemService.findByCallerRef(callerRef).map(WorkItemSpiAdapter::toRef);
    }

    @Override
    public Optional<WorkItemRef> findActiveByCallerRef(final String callerRef) {
        return workItemService.findActiveByCallerRef(callerRef).map(WorkItemSpiAdapter::toRef);
    }

    @Override
    public void cancel(final UUID id, final String actorId, final String reason) {
        try {
            workItemService.cancel(id, actorId, reason);
        } catch (final IllegalStateException e) {
            if (isTerminal(id)) return;
            throw e;
        }
    }

    @Override
    public void complete(final UUID id, final String actorId, final String resolution,
                         final String outcome, final String rationale, final String planRef) {
        try {
            workItemService.complete(id, actorId, resolution, outcome, rationale, planRef);
        } catch (final IllegalStateException e) {
            if (isTerminal(id)) return;
            throw e;
        }
    }

    @Override
    public void obsoleteByCallerRef(final String callerRef) {
        workItemService.findByCallerRef(callerRef).ifPresent(wi -> {
            try {
                workItemService.obsolete(wi.id, "system", "Consumed by caller");
            } catch (final IllegalStateException e) {
                if (isTerminal(wi.id)) return;
                throw e;
            }
        });
    }

    private boolean isTerminal(final UUID id) {
        return workItemService.findById(id)
                .map(wi -> wi.status != null && wi.status.isTerminal())
                .orElse(false);
    }

    static WorkItemRef toRef(final WorkItem wi) {
        return new WorkItemRef(
                wi.id, wi.status, wi.callerRef, wi.assigneeId,
                wi.resolution, wi.candidateGroups, wi.outcome, wi.tenancyId,
                wi.payload);
    }
}
