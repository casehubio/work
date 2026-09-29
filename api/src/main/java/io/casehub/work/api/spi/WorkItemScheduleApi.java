package io.casehub.work.api.spi;

import java.util.List;
import java.util.UUID;

import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestStatus;
import io.casehub.work.api.view.CreateScheduleRequest;
import io.casehub.work.api.view.ScheduleView;
import io.casehub.work.api.view.SetActiveRequest;

@McpDomain("work/schedules")
public interface WorkItemScheduleApi {

    @PlatformMutation("Create a new recurring schedule")
    @RestStatus(201)
    ScheduleView create(CreateScheduleRequest request,
                        @ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("List all schedules")
    List<ScheduleView> list(@ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("Get a schedule by ID")
    ScheduleView get(@PathParam UUID scheduleId,
                     @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Delete a schedule")
    void delete(@PathParam UUID scheduleId,
                @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Enable or disable a schedule")
    ScheduleView setActive(@PathParam UUID scheduleId, SetActiveRequest request,
                           @ContextParam("tenancyId") String tenancyId);
}
