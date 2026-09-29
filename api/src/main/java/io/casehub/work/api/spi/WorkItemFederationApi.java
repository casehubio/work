package io.casehub.work.api.spi;

import java.util.UUID;

import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.RestStatus;
import io.casehub.work.api.view.FederationSubscriptionRequest;
import io.casehub.work.api.view.FederationSubscriptionResult;

@McpDomain("work/federation")
public interface WorkItemFederationApi {

    @PlatformMutation("Register a federation subscription")
    @RestStatus(201)
    FederationSubscriptionResult register(FederationSubscriptionRequest request,
                                           @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Deregister a federation subscription")
    void deregister(@PathParam UUID subscriptionId,
                    @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Reactivate a federation subscription")
    FederationSubscriptionResult reactivate(@PathParam UUID subscriptionId,
                                             @ContextParam("tenancyId") String tenancyId);
}
