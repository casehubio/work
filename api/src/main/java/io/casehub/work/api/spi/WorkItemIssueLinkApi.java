package io.casehub.work.api.spi;

import java.util.List;
import java.util.UUID;

import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestStatus;
import io.casehub.work.api.view.CreateIssueRequest;
import io.casehub.work.api.view.IssueLinkView;
import io.casehub.work.api.view.LinkIssueRequest;
import io.casehub.work.api.view.SyncLinksResult;

@McpDomain("work/issue-links")
public interface WorkItemIssueLinkApi {

    @PlatformMutation("Link an existing issue to a work item")
    @RestStatus(201)
    IssueLinkView linkIssue(@PathParam UUID workItemId, LinkIssueRequest request,
                            @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Create a new issue and link it to a work item")
    @RestStatus(201)
    IssueLinkView createAndLink(@PathParam UUID workItemId, CreateIssueRequest request,
                                @ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("List all issues linked to a work item")
    List<IssueLinkView> listLinks(@PathParam UUID workItemId,
                                   @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Remove a link between a work item and an issue")
    void removeLink(@PathParam UUID workItemId, @PathParam UUID linkId,
                    @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Refresh status of all linked issues from remote trackers")
    SyncLinksResult syncLinks(@PathParam UUID workItemId,
                              @ContextParam("tenancyId") String tenancyId);
}
