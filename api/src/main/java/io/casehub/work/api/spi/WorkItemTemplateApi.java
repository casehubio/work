package io.casehub.work.api.spi;

import java.util.List;
import java.util.UUID;

import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestStatus;
import io.casehub.work.api.view.CreateTemplateRequest;
import io.casehub.work.api.view.InstantiateTemplateRequest;
import io.casehub.work.api.view.TemplateView;
import io.casehub.work.api.view.UpdateTemplateRequest;
import io.casehub.work.api.view.WorkItemView;

@McpDomain("work/templates")
public interface WorkItemTemplateApi {

    @PlatformMutation("Create a new work item template")
    @RestStatus(201)
    TemplateView create(CreateTemplateRequest request,
                        @ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("List all work item templates")
    List<TemplateView> listAll(@ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("Get a work item template by ID")
    TemplateView getById(@PathParam UUID templateId,
                         @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Delete a work item template")
    void delete(@PathParam UUID templateId,
                @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Update a work item template")
    TemplateView update(@PathParam UUID templateId, UpdateTemplateRequest request,
                        @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Create a work item from a template")
    @RestStatus(201)
    WorkItemView instantiate(@PathParam UUID templateId, InstantiateTemplateRequest request,
                             @ContextParam("tenancyId") String tenancyId);
}
