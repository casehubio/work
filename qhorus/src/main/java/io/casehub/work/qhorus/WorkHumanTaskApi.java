package io.casehub.work.qhorus;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@McpDomain(value = "work/human-tasks", basePath = "/api/work/human-tasks")
@ApplicationScoped
public class WorkHumanTaskApi {

    @Inject WorkQhorusMcpTools tools;

    @PlatformMutation("Request human work by creating a WorkItem and posting to a Qhorus channel")
    @RestPath("/request")
    public WorkQhorusMcpTools.HumanWorkResponse requestHumanWork(
            String channel, String title, String description,
            String candidateGroups, String priority, String payload,
            String templateId, String sender) {
        return tools.requestHumanWork(channel, title, description,
                candidateGroups, priority, payload, templateId, sender);
    }

    @PlatformQuery("Check the current status of a previously requested human work item")
    @RestPath("/status")
    public WorkQhorusMcpTools.WorkStatusResponse checkWorkStatus(String callerRef) {
        return tools.checkWorkStatus(callerRef);
    }

    @PlatformMutation("Poll until a human work item reaches a terminal state or times out")
    @RestPath("/wait")
    public WorkQhorusMcpTools.WorkStatusResponse waitForWork(
            String callerRef, int timeoutSeconds, int pollIntervalSeconds) {
        return tools.waitForWork(callerRef, timeoutSeconds, pollIntervalSeconds);
    }
}
