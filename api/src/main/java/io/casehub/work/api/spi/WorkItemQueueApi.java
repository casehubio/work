package io.casehub.work.api.spi;

import java.util.List;
import java.util.UUID;

import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestStatus;
import io.casehub.work.api.WorkItemSummary;
import io.casehub.work.api.view.CreateQueueRequest;
import io.casehub.work.api.view.CreateQueueResult;
import io.casehub.work.api.view.QueueHealthMetricView;
import io.casehub.work.api.view.QueueSummaryView;
import io.casehub.work.api.view.QueueTrendView;
import io.casehub.work.api.view.WorkItemView;

@McpDomain("work/queues")
public interface WorkItemQueueApi {

    @PlatformQuery("List all queues with summary")
    List<QueueSummaryView> list(@ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("Queue health metrics")
    List<QueueHealthMetricView> health(@ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Create a new queue")
    @RestStatus(201)
    CreateQueueResult create(CreateQueueRequest request,
                             @ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("Query queue members")
    List<WorkItemView> query(@PathParam UUID queueId,
                             @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Delete a queue")
    void delete(@PathParam UUID queueId,
                @ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("Queue summary statistics")
    WorkItemSummary summary(@PathParam UUID queueId,
                            @ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("Queue trend data over a time period")
    QueueTrendView trend(@PathParam UUID queueId, String period,
                         @ContextParam("tenancyId") String tenancyId);
}
