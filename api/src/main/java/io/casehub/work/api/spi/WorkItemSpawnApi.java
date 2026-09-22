package io.casehub.work.api.spi;

import java.util.List;
import java.util.UUID;

import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestStatus;
import io.casehub.work.api.view.SpawnBodyRequest;
import io.casehub.work.api.view.SpawnGroupSummaryView;
import io.casehub.work.api.view.SpawnResultView;

@McpDomain("work/spawn")
public interface WorkItemSpawnApi {

    @PlatformMutation("Spawn a group of child work items from a parent")
    @RestStatus(201)
    SpawnResultView spawn(@PathParam UUID workItemId, SpawnBodyRequest body,
                          @ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("List all spawn groups for a parent work item")
    List<SpawnGroupSummaryView> listSpawnGroups(@PathParam UUID workItemId,
                                                @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Cancel a spawn group")
    void cancelGroup(@PathParam UUID workItemId, @PathParam UUID groupId,
                     boolean cancelChildren,
                     @ContextParam("tenancyId") String tenancyId);
}
