package io.casehub.work.api.spi;

import java.util.List;

import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestStatus;
import io.casehub.work.api.view.SkillProfileRequest;
import io.casehub.work.api.view.SkillProfileView;

@McpDomain("work/skill-profiles")
public interface WorkItemSkillProfileApi {

    @PlatformMutation("Upsert a worker skill profile")
    @RestStatus(201)
    void upsert(SkillProfileRequest request,
                @ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("List all worker skill profiles")
    List<SkillProfileView> listAll(@ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("Get a worker skill profile by worker ID")
    SkillProfileView get(@PathParam String workerId,
                         @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Delete a worker skill profile")
    void delete(@PathParam String workerId,
                @ContextParam("tenancyId") String tenancyId);
}
