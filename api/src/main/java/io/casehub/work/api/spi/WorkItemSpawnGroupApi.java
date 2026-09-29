package io.casehub.work.api.spi;

import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.work.api.view.SpawnGroupView;

import java.util.UUID;

@McpDomain("work/spawn-groups")
public interface WorkItemSpawnGroupApi {

    @PlatformQuery("Get a spawn group by ID with its children")
    SpawnGroupView getGroup(@PathParam UUID groupId,
                            @ContextParam("tenancyId") String tenancyId);
}
