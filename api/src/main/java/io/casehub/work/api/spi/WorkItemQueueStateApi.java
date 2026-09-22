package io.casehub.work.api.spi;

import java.util.UUID;

import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.work.api.view.RelinquishableRequest;
import io.casehub.work.api.view.RelinquishableResult;
import io.casehub.work.api.view.WorkItemView;

@McpDomain("work/queue-state")
public interface WorkItemQueueStateApi {

    @PlatformMutation("Pick up a work item from a queue")
    WorkItemView pickup(@PathParam UUID workItemId, String claimant,
                        @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Set relinquishable flag on a work item")
    RelinquishableResult setRelinquishable(@PathParam UUID workItemId,
                                           RelinquishableRequest request,
                                           @ContextParam("tenancyId") String tenancyId);
}
