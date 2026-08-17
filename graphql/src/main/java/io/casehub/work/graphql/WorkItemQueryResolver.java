package io.casehub.work.graphql;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.graphql.PageInfo;
import io.casehub.platform.graphql.PageInput;
import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.api.WorkItemQuery;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.api.WorkItemSummary;
import io.casehub.work.api.spi.WorkItemStore;
import io.casehub.work.graphql.dto.WorkItemFilterInput;
import io.casehub.work.graphql.dto.WorkItemPage;
import io.casehub.work.graphql.dto.WorkItemType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@GraphQLApi
@McpDomain("work")
@ApplicationScoped
public class WorkItemQueryResolver {

    @Inject WorkItemStore store;
    @Inject CurrentPrincipal currentPrincipal;

    @Query
    @Description("List work items with optional filtering and pagination")
    public WorkItemPage workItems(WorkItemFilterInput filter, PageInput page) {
        int offset = page != null && page.offset() != null ? page.offset() : 0;
        int limit = page != null && page.limit() != null ? page.limit() : 20;

        var queryBuilder = WorkItemQuery.builder()
            .tenancyId(currentPrincipal.tenancyId());

        if (filter != null) {
            if (filter.status() != null) queryBuilder.status(WorkItemStatus.valueOf(filter.status()));
            if (filter.assignee() != null) queryBuilder.assigneeId(filter.assignee());
            if (filter.priority() != null) queryBuilder.priority(WorkItemPriority.valueOf(filter.priority()));
        }

        var query = queryBuilder.build();
        List<WorkItemType> all = store.scan(query).stream().map(WorkItemType::from).toList();
        int total = all.size();
        int end = Math.min(offset + limit, total);
        List<WorkItemType> items = offset < total ? all.subList(offset, end) : List.of();

        return new WorkItemPage(items, new PageInfo(end < total, offset > 0, total, null));
    }

    @Query
    @Description("Retrieve a single work item by its unique identifier")
    public WorkItemType workItemById(UUID id) {
        return store.get(id).map(WorkItemType::from).orElse(null);
    }

    @Query
    @Description("List work items assigned to or claimable by a user — the user's inbox")
    public WorkItemPage workItemInbox(String assignee, List<String> candidateGroups, PageInput page) {
        int offset = page != null && page.offset() != null ? page.offset() : 0;
        int limit = page != null && page.limit() != null ? page.limit() : 20;

        var queryBuilder = WorkItemQuery.builder()
            .tenancyId(currentPrincipal.tenancyId());

        if (assignee != null) queryBuilder.assigneeId(assignee);
        if (candidateGroups != null) queryBuilder.candidateGroups(candidateGroups);

        var query = queryBuilder.build();
        List<WorkItemType> all = store.scan(query).stream().map(WorkItemType::from).toList();
        int total = all.size();
        int end = Math.min(offset + limit, total);
        List<WorkItemType> items = offset < total ? all.subList(offset, end) : List.of();

        return new WorkItemPage(items, new PageInfo(end < total, offset > 0, total, null));
    }

    @Query
    @Description("Summary statistics for a user's inbox — counts by status and priority")
    public WorkItemSummary workItemInboxSummary(String assignee) {
        var query = WorkItemQuery.builder()
            .tenancyId(currentPrincipal.tenancyId())
            .assigneeId(assignee)
            .build();
        return store.summaryByQuery(query, Instant.now());
    }
}
