package io.casehub.work.api.spi;

import java.util.UUID;

import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.work.api.view.InstancesView;

@McpDomain("work/instances")
public interface WorkItemInstanceApi {

    @PlatformQuery("Get child instances of a multi-instance work item")
    InstancesView getInstances(@PathParam UUID workItemId,
                               @ContextParam("tenancyId") String tenancyId);
}
