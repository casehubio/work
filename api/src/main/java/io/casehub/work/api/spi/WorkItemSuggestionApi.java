package io.casehub.work.api.spi;

import java.util.UUID;

import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.work.api.view.ResolutionSuggestionView;

@McpDomain("work/suggestions")
public interface WorkItemSuggestionApi {

    @PlatformQuery("Get an AI-suggested resolution for a work item")
    ResolutionSuggestionView suggest(@PathParam UUID workItemId,
                                         @ContextParam("tenancyId") String tenancyId);
}
