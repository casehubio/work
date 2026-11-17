package io.casehub.work.api.spi;

import java.util.UUID;

import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
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

@McpDomain("work/lifecycle")
public interface WorkItemLifecycleApi {

    @PlatformMutation("Claim a work item")
    WorkItemView claim(@PathParam UUID workItemId,
                       @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Start a claimed work item")
    WorkItemView start(@PathParam UUID workItemId,
                       @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Complete a work item")
    WorkItemView complete(@PathParam UUID workItemId, CompleteRequest body,
                          @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Reject a work item")
    WorkItemView reject(@PathParam UUID workItemId, RejectRequest body,
                        @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Delegate a work item")
    WorkItemView delegate(@PathParam UUID workItemId, DelegateRequest body,
                          @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Accept a delegation")
    WorkItemView acceptDelegation(@PathParam UUID workItemId,
                                  @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Decline a delegation")
    WorkItemView declineDelegation(@PathParam UUID workItemId,
                                   @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Release a claimed work item")
    WorkItemView release(@PathParam UUID workItemId,
                         @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Suspend a work item")
    WorkItemView suspend(@PathParam UUID workItemId, SuspendRequest body,
                         @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Resume a suspended work item")
    WorkItemView resume(@PathParam UUID workItemId,
                        @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Cancel a work item")
    WorkItemView cancel(@PathParam UUID workItemId, CancelRequest body,
                        @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Mark a work item as faulted")
    WorkItemView fault(@PathParam UUID workItemId, FaultRequest body,
                       @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Mark a work item as obsolete")
    WorkItemView obsolete(@PathParam UUID workItemId, ObsoleteRequest body,
                          @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Escalate a work item")
    WorkItemView escalate(@PathParam UUID workItemId, EscalateRequest body,
                          @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Extend a work item deadline")
    WorkItemView extend(@PathParam UUID workItemId, ExtendRequest body,
                        @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Update deadline")
    WorkItemView updateDeadline(@PathParam UUID workItemId,
                                UpdateDeadlineRequest body,
                                @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Create a compensating work item")
    WorkItemView compensate(@PathParam UUID workItemId, CompensateRequest body,
                            @ContextParam("tenancyId") String tenancyId);
}
