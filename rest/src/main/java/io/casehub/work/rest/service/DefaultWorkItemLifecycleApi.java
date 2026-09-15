package io.casehub.work.rest.service;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.work.api.WorkItem;
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

    @Override
    public WorkItemView claim(UUID workItemId, String claimant, String tenancyId) {
        return ViewMapper.toView(workItemOperations.claim(workItemId, claimant));
    }

    @Override
    public WorkItemView start(UUID workItemId, String actor, String tenancyId) {
        return ViewMapper.toView(workItemOperations.start(workItemId, actor));
    }

    @Override
    public WorkItemView complete(UUID workItemId, String actor, CompleteRequest body, String tenancyId) {
        String resolution = body != null ? body.resolution() : null;
        String outcome = body != null ? body.outcome() : null;
        return ViewMapper.toView(workItemOperations.complete(workItemId, actor, resolution, outcome));
    }

    @Override
    public WorkItemView reject(UUID workItemId, String actor, RejectRequest body, String tenancyId) {
        String reason = body != null ? body.reason() : null;
        String outcome = body != null ? body.outcome() : null;
        return ViewMapper.toView(workItemOperations.reject(workItemId, actor, reason, outcome));
    }

    @Override
    public WorkItemView delegate(UUID workItemId, String actor, DelegateRequest body, String tenancyId) {
        return ViewMapper.toView(
                workItemOperations.delegate(workItemId, actor, body.to(), body.declineTarget()));
    }

    @Override
    public WorkItemView acceptDelegation(UUID workItemId, String claimant, String tenancyId) {
        return ViewMapper.toView(workItemOperations.acceptDelegation(workItemId, claimant));
    }

    @Override
    public WorkItemView declineDelegation(UUID workItemId, String actor, String tenancyId) {
        return ViewMapper.toView(workItemOperations.declineDelegation(workItemId, actor));
    }

    @Override
    public WorkItemView release(UUID workItemId, String actor, String tenancyId) {
        return ViewMapper.toView(workItemOperations.release(workItemId, actor));
    }

    @Override
    public WorkItemView suspend(UUID workItemId, String actor, SuspendRequest body, String tenancyId) {
        String reason = body != null ? body.reason() : null;
        return ViewMapper.toView(workItemOperations.suspend(workItemId, actor, reason));
    }

    @Override
    public WorkItemView resume(UUID workItemId, String actor, String tenancyId) {
        return ViewMapper.toView(workItemOperations.resume(workItemId, actor));
    }

    @Override
    public WorkItemView cancel(UUID workItemId, String actor, CancelRequest body, String tenancyId) {
        String reason = body != null ? body.reason() : null;
        return ViewMapper.toView(workItemOperations.cancel(workItemId, actor, reason));
    }

    @Override
    public WorkItemView fault(UUID workItemId, FaultRequest body, String tenancyId) {
        return ViewMapper.toView(
                workItemOperations.fault(workItemId, body.actor(), body.errorDetail()));
    }

    @Override
    public WorkItemView obsolete(UUID workItemId, ObsoleteRequest body, String tenancyId) {
        return ViewMapper.toView(
                workItemOperations.obsolete(workItemId, body.actor(), body.reason()));
    }

    @Override
    public WorkItemView escalate(UUID workItemId, String actor, EscalateRequest body, String tenancyId) {
        return ViewMapper.toView(
                workItemOperations.escalate(workItemId, actor, body.targetGroup(), body.reason()));
    }

    @Override
    public WorkItemView extend(UUID workItemId, String actor, ExtendRequest body, String tenancyId) {
        return ViewMapper.toView(
                workItemOperations.extend(workItemId, body != null ? body.newExpiresAt() : null, actor));
    }

    @Override
    public WorkItemView updateDeadline(UUID workItemId, String actor, UpdateDeadlineRequest body,
                                       String tenancyId) {
        return ViewMapper.toView(
                workItemOperations.updateDeadline(workItemId, body != null ? body.newDeadline() : null, actor));
    }

    @Override
    public WorkItemView compensate(UUID workItemId, CompensateRequest body, String tenancyId) {
        WorkItemCreateRequest request = WorkItemCreateRequest.builder()
                .title(body.title())
                .candidateGroups(body.candidateGroups())
                .createdBy(body.actor())
                .build();
        return ViewMapper.toView(
                workItemOperations.compensate(workItemId, request, body.actor(), body.reason()));
    }
}
