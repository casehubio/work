package io.casehub.work.api.spi;

import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.work.api.view.ActorReportView;
import io.casehub.work.api.view.QueueHealthReportView;
import io.casehub.work.api.view.SlaBreachReportView;
import io.casehub.work.api.view.ThroughputReportView;

@McpDomain("work/reports")
public interface WorkItemReportApi {

    @PlatformQuery("SLA breach report")
    SlaBreachReportView slaBreaches(String from, String to, String type, String priority,
                                     @ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("Actor performance report")
    ActorReportView actorPerformance(@PathParam String actorId, String from, String to, String type,
                                     @ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("Throughput report")
    ThroughputReportView throughput(String from, String to, String groupBy,
                                    @ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("Queue health report")
    QueueHealthReportView queueHealth(String type, String priority,
                                       @ContextParam("tenancyId") String tenancyId);
}
