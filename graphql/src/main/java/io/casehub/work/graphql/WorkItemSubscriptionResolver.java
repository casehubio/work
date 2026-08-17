package io.casehub.work.graphql;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.work.graphql.dto.WorkItemLifecycleEventType;
import io.smallrye.graphql.api.Subscription;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.UUID;
import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Name;

@GraphQLApi
@McpDomain("work")
@ApplicationScoped
public class WorkItemSubscriptionResolver {

    private final WorkItemEventPublisher publisher;

    @Inject
    public WorkItemSubscriptionResolver(WorkItemEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Subscription
    @Description("Live work item lifecycle events for a specific work item")
    public Multi<WorkItemLifecycleEventType> workItemLifecycle(@Name("id") UUID id) {
        return publisher.lifecycleStream()
            .filter(event -> id == null || id.equals(event.workItemId()))
            .map(WorkItemLifecycleEventType::from);
    }

    @Subscription
    @Description("Live inbox updates — lifecycle events for work items assigned to a user")
    public Multi<WorkItemLifecycleEventType> workItemInboxUpdates(@Name("assignee") String assignee) {
        return publisher.lifecycleStream()
            .filter(event -> assignee.equals(event.assigneeId()))
            .map(WorkItemLifecycleEventType::from);
    }
}
