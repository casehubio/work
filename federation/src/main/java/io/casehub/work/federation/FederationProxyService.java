package io.casehub.work.federation;

import io.casehub.work.api.DeclineTarget;
import io.casehub.work.api.WorkItem;
import io.casehub.work.api.WorkItemCreateRequest;
import io.casehub.work.api.WorkItemQuery;
import io.casehub.work.api.spi.WorkItemOperations;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Decorator
@Priority(Interceptor.Priority.APPLICATION + 10)
public class FederationProxyService implements WorkItemOperations {

    @Delegate
    @Inject
    @Any
    WorkItemOperations delegate;

    @Inject
    FederationProxy proxy;

    private Optional<WorkItem> findShadow(UUID id) {
        return delegate.findById(id).filter(wi -> wi.originServiceId() != null);
    }

    @Override
    public WorkItem claim(UUID id, String claimantId) {
        return findShadow(id)
                .map(shadow -> proxy.claim(shadow, claimantId))
                .orElseGet(() -> delegate.claim(id, claimantId));
    }

    @Override
    public WorkItem complete(UUID id, String actorId, String resolution, String outcome) {
        return findShadow(id)
                .map(shadow -> proxy.complete(shadow, actorId, resolution, outcome))
                .orElseGet(() -> delegate.complete(id, actorId, resolution, outcome));
    }

    @Override
    public WorkItem complete(UUID id, String actorId, String resolution, String outcome, String rationale, String planRef) {
        return findShadow(id)
                .map(shadow -> proxy.complete(shadow, actorId, resolution, outcome))
                .orElseGet(() -> delegate.complete(id, actorId, resolution, outcome, rationale, planRef));
    }

    @Override
    public WorkItem reject(UUID id, String actorId, String reason, String outcome) {
        return findShadow(id)
                .map(shadow -> proxy.reject(shadow, actorId, reason, outcome))
                .orElseGet(() -> delegate.reject(id, actorId, reason, outcome));
    }

    @Override
    public WorkItem reject(UUID id, String actorId, String reason, String outcome, String rationale) {
        return findShadow(id)
                .map(shadow -> proxy.reject(shadow, actorId, reason, outcome))
                .orElseGet(() -> delegate.reject(id, actorId, reason, outcome, rationale));
    }

    @Override
    public WorkItem delegate(UUID id, String actorId, String toAssigneeId, DeclineTarget declineTarget) {
        return findShadow(id)
                .map(shadow -> proxy.delegate(shadow, actorId, toAssigneeId))
                .orElseGet(() -> delegate.delegate(id, actorId, toAssigneeId, declineTarget));
    }

    @Override
    public WorkItem release(UUID id, String actorId) {
        return findShadow(id)
                .map(shadow -> proxy.release(shadow, actorId))
                .orElseGet(() -> delegate.release(id, actorId));
    }

    // All other methods delegate transparently

    @Override public WorkItem create(WorkItemCreateRequest request) { return delegate.create(request); }
    @Override public WorkItem start(UUID id, String actorId) { return delegate.start(id, actorId); }
    @Override public WorkItem completeFromSystem(UUID id, String actorId, String resolution) { return delegate.completeFromSystem(id, actorId, resolution); }
    @Override public WorkItem rejectFromSystem(UUID id, String actorId, String reason) { return delegate.rejectFromSystem(id, actorId, reason); }
    @Override public WorkItem acceptDelegation(UUID id, String claimantId) { return delegate.acceptDelegation(id, claimantId); }
    @Override public WorkItem declineDelegation(UUID id, String actorId) { return delegate.declineDelegation(id, actorId); }
    @Override public WorkItem suspend(UUID id, String actorId, String reason) { return delegate.suspend(id, actorId, reason); }
    @Override public WorkItem resume(UUID id, String actorId) { return delegate.resume(id, actorId); }
    @Override public WorkItem cancel(UUID id, String actorId, String reason) { return delegate.cancel(id, actorId, reason); }
    @Override public WorkItem cancelFromSystem(UUID id, String actorId, String reason) { return delegate.cancelFromSystem(id, actorId, reason); }
    @Override public WorkItem fault(UUID id, String systemActorId, String errorDetail) { return delegate.fault(id, systemActorId, errorDetail); }
    @Override public WorkItem faultFromSystem(UUID id, String actorId, String errorDetail) { return delegate.faultFromSystem(id, actorId, errorDetail); }
    @Override public WorkItem obsolete(UUID id, String triggeredBy, String reason) { return delegate.obsolete(id, triggeredBy, reason); }
    @Override public WorkItem obsoleteFromSystem(UUID id, String triggeredBy, String reason) { return delegate.obsoleteFromSystem(id, triggeredBy, reason); }
    @Override public WorkItem escalate(UUID id, String actor, String targetGroup, String reason) { return delegate.escalate(id, actor, targetGroup, reason); }
    @Override public WorkItem extend(UUID id, Instant newExpiresAt, String actorId) { return delegate.extend(id, newExpiresAt, actorId); }
    @Override public WorkItem updateDeadline(UUID id, Instant newDeadline, String actorId) { return delegate.updateDeadline(id, newDeadline, actorId); }
    @Override public WorkItem addLabel(UUID workItemId, String path, String appliedBy) { return delegate.addLabel(workItemId, path, appliedBy); }
    @Override public WorkItem removeLabel(UUID workItemId, String path) { return delegate.removeLabel(workItemId, path); }
    @Override public WorkItem clone(UUID sourceId, String titleOverride, String createdBy) { return delegate.clone(sourceId, titleOverride, createdBy); }
    @Override public Optional<WorkItem> findById(UUID id) { return delegate.findById(id); }
    @Override public List<WorkItem> scan(WorkItemQuery query) { return delegate.scan(query); }
    @Override public List<WorkItem> findChildrenByParentId(UUID parentId) { return delegate.findChildrenByParentId(parentId); }
    @Override public Optional<WorkItem> findByCallerRef(String callerRef) { return delegate.findByCallerRef(callerRef); }
    @Override public Optional<WorkItem> findActiveByCallerRef(String callerRef) { return delegate.findActiveByCallerRef(callerRef); }
}
