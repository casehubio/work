package io.casehub.work.rest.service;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.work.api.WorkItemCreateRequest;
import io.casehub.work.api.spi.WorkItemLifecycleApi;
import io.casehub.work.api.spi.WorkItemOperations;
import io.casehub.work.api.view.CancelRequest;
import io.casehub.work.api.view.CompensateRequest;
import io.casehub.work.api.view.CompleteRequest;
import io.casehub.work.api.view.DelegateRequest;
import io.casehub.work.api.view.EscalateRequest;
import io.casehub.work.api.view.ExtendRequest;
import io.casehub.work.api.view.FaultRequest;
import io.casehub.work.api.view.ObsoleteRequest;
import io.casehub.work.api.view.RejectRequest;
import io.casehub.work.api.view.SuspendRequest;
import io.casehub.work.api.view.UpdateDeadlineRequest;
import io.casehub.work.api.view.WorkItemView;

@ApplicationScoped
public class DefaultWorkItemLifecycleApi implements WorkItemLifecycleApi {

    @Inject
    WorkItemOperations workItemOperations;

    @Inject
    CurrentPrincipal currentPrincipal;

    @Override
    public WorkItemView claim(UUID workItemId, String tenancyId) {
        return ViewMapper.toView(workItemOperations.claim(workItemId, currentPrincipal.actorId()));
    }

    @Override
    public WorkItemView start(UUID workItemId, String tenancyId) {
        return ViewMapper.toView(workItemOperations.start(workItemId, currentPrincipal.actorId()));
    }

    @Override
    public WorkItemView complete(UUID workItemId, CompleteRequest body, String tenancyId) {
        String resolution = body != null ? body.resolution() : null;
        String outcome = body != null ? body.outcome() : null;
        return ViewMapper.toView(workItemOperations.complete(workItemId, currentPrincipal.actorId(), resolution, outcome));
    }

    @Override
    public WorkItemView reject(UUID workItemId, RejectRequest body, String tenancyId) {
        String reason = body != null ? body.reason() : null;
        String outcome = body != null ? body.outcome() : null;
        return ViewMapper.toView(workItemOperations.reject(workItemId, currentPrincipal.actorId(), reason, outcome));
    }

    @Override
    public WorkItemView delegate(UUID workItemId, DelegateRequest body, String tenancyId) {
        return ViewMapper.toView(
                workItemOperations.delegate(workItemId, currentPrincipal.actorId(), body.to(), body.declineTarget()));
    }

    @Override
    public WorkItemView acceptDelegation(UUID workItemId, String tenancyId) {
        return ViewMapper.toView(workItemOperations.acceptDelegation(workItemId, currentPrincipal.actorId()));
    }

    @Override
    public WorkItemView declineDelegation(UUID workItemId, String tenancyId) {
        return ViewMapper.toView(workItemOperations.declineDelegation(workItemId, currentPrincipal.actorId()));
    }

    @Override
    public WorkItemView release(UUID workItemId, String tenancyId) {
        return ViewMapper.toView(workItemOperations.release(workItemId, currentPrincipal.actorId()));
    }

    @Override
    public WorkItemView suspend(UUID workItemId, SuspendRequest body, String tenancyId) {
        String reason = body != null ? body.reason() : null;
        return ViewMapper.toView(workItemOperations.suspend(workItemId, currentPrincipal.actorId(), reason));
    }

    @Override
    public WorkItemView resume(UUID workItemId, String tenancyId) {
        return ViewMapper.toView(workItemOperations.resume(workItemId, currentPrincipal.actorId()));
    }

    @Override
    public WorkItemView cancel(UUID workItemId, CancelRequest body, String tenancyId) {
        String reason = body != null ? body.reason() : null;
        return ViewMapper.toView(workItemOperations.cancel(workItemId, currentPrincipal.actorId(), reason));
    }

    @Override
    public WorkItemView fault(UUID workItemId, FaultRequest body, String tenancyId) {
        return ViewMapper.toView(
                workItemOperations.fault(workItemId, currentPrincipal.actorId(), body.errorDetail()));
    }

    @Override
    public WorkItemView obsolete(UUID workItemId, ObsoleteRequest body, String tenancyId) {
        return ViewMapper.toView(
                workItemOperations.obsolete(workItemId, currentPrincipal.actorId(), body.reason()));
    }

    @Override
    public WorkItemView escalate(UUID workItemId, EscalateRequest body, String tenancyId) {
        return ViewMapper.toView(
                workItemOperations.escalate(workItemId, currentPrincipal.actorId(), body.targetGroup(), body.reason()));
    }

    @Override
    public WorkItemView extend(UUID workItemId, ExtendRequest body, String tenancyId) {
        return ViewMapper.toView(
                workItemOperations.extend(workItemId, body != null ? body.newExpiresAt() : null, currentPrincipal.actorId()));
    }

    @Override
    public WorkItemView updateDeadline(UUID workItemId, UpdateDeadlineRequest body,
                                       String tenancyId) {
        return ViewMapper.toView(
                workItemOperations.updateDeadline(workItemId, body != null ? body.newDeadline() : null, currentPrincipal.actorId()));
    }

    @Override
    public WorkItemView compensate(UUID workItemId, CompensateRequest body, String tenancyId) {
        WorkItemCreateRequest request = WorkItemCreateRequest.builder()
                .title(body.title())
                .candidateGroups(body.candidateGroups())
                .createdBy(currentPrincipal.actorId())
                .build();
        return ViewMapper.toView(
                workItemOperations.compensate(workItemId, request, currentPrincipal.actorId(), body.reason()));
    }
}
