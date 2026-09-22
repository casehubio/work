package io.casehub.work.api.spi;

import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PaginatedResponse;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.work.api.view.AuditQueryResult;

@McpDomain("work/audit")
public interface WorkItemAuditApi {

    @PlatformQuery("Query audit history across all WorkItems")
    @PaginatedResponse
    AuditQueryResult query(String actorId, String from, String to, String event, String type,
                           Integer pageIndex, Integer pageSize,
                           @ContextParam("tenancyId") String tenancyId);
}
