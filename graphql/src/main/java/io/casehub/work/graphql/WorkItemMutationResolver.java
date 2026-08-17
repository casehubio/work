package io.casehub.work.graphql;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.work.api.WorkItemCreateRequest;
import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.graphql.dto.CreateWorkItemInput;
import io.casehub.work.graphql.dto.WorkItemType;
import io.casehub.work.runtime.service.WorkItemService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.UUID;
import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Mutation;

@GraphQLApi
@McpDomain("work")
@ApplicationScoped
public class WorkItemMutationResolver {

    @Inject WorkItemService workItemService;
    @Inject CurrentPrincipal currentPrincipal;

    @Mutation
    @Description("Create a new work item with title, description, and assignment criteria")
    public WorkItemType createWorkItem(CreateWorkItemInput input) {
        var builder = WorkItemCreateRequest.builder()
            .title(input.title())
            .description(input.description())
            .tenancyId(currentPrincipal.tenancyId())
            .createdBy(currentPrincipal.actorId());

        if (input.formKey() != null) builder.formKey(input.formKey());
        if (input.priority() != null) builder.priority(WorkItemPriority.valueOf(input.priority()));
        if (input.candidateGroups() != null) builder.candidateGroups(input.candidateGroups());
        if (input.candidateUsers() != null) builder.candidateUsers(input.candidateUsers());
        if (input.requiredCapabilities() != null) builder.requiredCapabilities(input.requiredCapabilities());
        if (input.types() != null) builder.types(input.types());
        if (input.scope() != null) builder.scope(input.scope());
        if (input.expiresAt() != null) builder.expiresAt(input.expiresAt());
        if (input.claimDeadline() != null) builder.claimDeadline(input.claimDeadline());
        if (input.payload() != null) builder.payload(input.payload());

        return WorkItemType.from(workItemService.create(builder.build()));
    }

    @Mutation
    @Description("Claim a work item — assigns it to the specified claimant")
    public WorkItemType claimWorkItem(UUID id, String claimant) {
        return WorkItemType.from(workItemService.claim(id, claimant));
    }

    @Mutation
    @Description("Start working on a claimed work item")
    public WorkItemType startWorkItem(UUID id) {
        return WorkItemType.from(workItemService.start(id, currentPrincipal.actorId()));
    }

    @Mutation
    @Description("Complete a work item with a resolution and optional outcome")
    public WorkItemType completeWorkItem(UUID id, String resolution, String outcome) {
        return WorkItemType.from(workItemService.complete(id, currentPrincipal.actorId(), resolution, outcome));
    }

    @Mutation
    @Description("Reject a work item with a reason")
    public WorkItemType rejectWorkItem(UUID id, String reason) {
        return WorkItemType.from(workItemService.reject(id, currentPrincipal.actorId(), reason, null));
    }

    @Mutation
    @Description("Delegate a work item to another actor")
    public WorkItemType delegateWorkItem(UUID id, String targetActor) {
        return WorkItemType.from(workItemService.delegate(id, currentPrincipal.actorId(), targetActor, null));
    }

    @Mutation
    @Description("Suspend a work item — pauses the work in progress")
    public WorkItemType suspendWorkItem(UUID id) {
        return WorkItemType.from(workItemService.suspend(id, currentPrincipal.actorId(), null));
    }

    @Mutation
    @Description("Resume a previously suspended work item")
    public WorkItemType resumeWorkItem(UUID id) {
        return WorkItemType.from(workItemService.resume(id, currentPrincipal.actorId()));
    }

    @Mutation
    @Description("Cancel a work item — terminally closes it")
    public WorkItemType cancelWorkItem(UUID id, String reason) {
        return WorkItemType.from(workItemService.cancel(id, currentPrincipal.actorId(), reason));
    }

    @Mutation
    @Description("Escalate a work item to a target group")
    public WorkItemType escalateWorkItem(UUID id, String targetGroup, String reason) {
        return WorkItemType.from(workItemService.escalate(id, currentPrincipal.actorId(), targetGroup, reason));
    }
}
