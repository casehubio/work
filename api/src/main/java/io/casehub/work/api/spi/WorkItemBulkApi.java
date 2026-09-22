package io.casehub.work.api.spi;

import java.util.List;

import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.work.api.view.BulkItemResult;
import io.casehub.work.api.view.BulkOperationRequest;

@McpDomain("work/bulk")
public interface WorkItemBulkApi {

    @PlatformMutation("Execute bulk operations across multiple work items")
    List<BulkItemResult> bulk(BulkOperationRequest request,
                              @ContextParam("tenancyId") String tenancyId);
}
