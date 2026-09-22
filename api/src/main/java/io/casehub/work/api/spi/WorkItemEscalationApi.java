package io.casehub.work.api.spi;

import java.util.List;
import java.util.UUID;

import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.work.api.view.EscalationSummaryView;

@McpDomain("work/escalations")
public interface WorkItemEscalationApi {

    @PlatformQuery("List escalation summaries for a work item")
    List<EscalationSummaryView> list(@PathParam UUID workItemId,
                                     @ContextParam("tenancyId") String tenancyId);
}
