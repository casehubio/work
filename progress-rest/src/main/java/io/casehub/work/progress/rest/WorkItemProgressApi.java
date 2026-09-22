package io.casehub.work.progress.rest;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.PlatformStream;
import io.casehub.platform.api.mcp.RestStatus;
import io.casehub.work.progress.ProgressCreateRequest;
import io.casehub.work.progress.ProgressInstance;
import io.casehub.work.progress.ProgressSnapshot;
import io.casehub.work.progress.ProgressUpdatedEvent;
import io.casehub.work.progress.SubtreeRollbackResult;
import io.casehub.work.progress.runtime.event.ProgressEventBroadcaster;
import io.casehub.work.progress.runtime.service.ProgressService;
import io.casehub.work.progress.runtime.service.SubtreeRollbackService;
import io.casehub.work.progress.spi.ProgressEventStore;
import io.casehub.work.progress.spi.ProgressInstanceStore;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@McpDomain("work/progress")
@ApplicationScoped
public class WorkItemProgressApi {

    @Inject ProgressService progressService;
    @Inject ProgressEventStore eventStore;
    @Inject ProgressInstanceStore instanceStore;
    @Inject ProgressEventBroadcaster broadcaster;
    @Inject SubtreeRollbackService subtreeRollbackService;

    @PlatformMutation("Create a new progress instance")
    @RestStatus(201)
    public ProgressInstance create(CreateProgressRequest request,
                                  @ContextParam("tenancyId") String tenancyId) {
        ProgressCreateRequest domainReq = new ProgressCreateRequest(
                request.tenancyId() != null ? request.tenancyId() : tenancyId,
                request.scopeType(), request.scopeId(),
                request.shapeType(), request.state(),
                request.parentProgressId(), request.rollupStrategyId(),
                request.definition(), request.rollbackPolicy(),
                request.visualisationMode(), null);
        return progressService.create(domainReq);
    }

    @PlatformQuery("Get a progress instance by ID")
    public ProgressInstance getById(@PathParam UUID progressId,
                                   @ContextParam("tenancyId") String tenancyId) {
        return progressService.findById(progressId).orElse(null);
    }

    @PlatformQuery("Find progress instances by scope")
    public List<ProgressInstance> findByScope(String scopeType, String scopeId,
                                             @ContextParam("tenancyId") String tenancyId) {
        return progressService.findByScope(scopeType, scopeId);
    }

    @PlatformMutation("Update the state of a progress instance")
    public ProgressInstance updateState(@PathParam UUID progressId, JsonNode state,
                                       @ContextParam("tenancyId") String tenancyId) {
        return progressService.updateState(progressId, state);
    }

    @PlatformMutation("Mark a progress instance as complete")
    public ProgressInstance complete(@PathParam UUID progressId,
                                    @ContextParam("tenancyId") String tenancyId) {
        return progressService.complete(progressId);
    }

    @PlatformMutation("Mark a progress instance as failed")
    public ProgressInstance fail(@PathParam UUID progressId,
                                @ContextParam("tenancyId") String tenancyId) {
        return progressService.fail(progressId);
    }

    @PlatformMutation("Reactivate a completed or failed progress instance")
    public ProgressInstance reactivate(@PathParam UUID progressId,
                                      @ContextParam("tenancyId") String tenancyId) {
        return progressService.reactivate(progressId);
    }

    @PlatformMutation("Attach a child progress instance to a parent")
    @RestStatus(201)
    public ProgressInstance attachChild(@PathParam UUID parentId, CreateProgressRequest request,
                                       @ContextParam("tenancyId") String tenancyId) {
        ProgressCreateRequest domainReq = new ProgressCreateRequest(
                request.tenancyId() != null ? request.tenancyId() : tenancyId,
                request.scopeType(), request.scopeId(),
                request.shapeType(), request.state(),
                null, request.rollupStrategyId(), request.definition(),
                request.rollbackPolicy(), request.visualisationMode(), null);
        return progressService.attachChild(parentId, domainReq);
    }

    @PlatformQuery("Get the progress tree rooted at a given instance")
    public ProgressTreeView getTree(@PathParam UUID progressId,
                                   @ContextParam("tenancyId") String tenancyId) {
        return progressService.findById(progressId)
                .map(root -> new ProgressTreeView(root, instanceStore.findDescendantsOf(progressId)))
                .orElse(null);
    }

    @PlatformQuery("Get progress events for an instance")
    public List<ProgressUpdatedEvent> getEvents(@PathParam UUID progressId, String since,
                                                @ContextParam("tenancyId") String tenancyId) {
        if (since != null) {
            return eventStore.findByProgressIdSince(progressId, Instant.parse(since));
        }
        return eventStore.findByProgressId(progressId);
    }

    @PlatformMutation("Rollback a progress instance to its previous state or to a specific event")
    public ProgressInstance rollback(@PathParam UUID progressId, UUID toEventId,
                                    @ContextParam("tenancyId") String tenancyId) {
        if (toEventId != null) {
            return progressService.rollbackToEvent(progressId, toEventId);
        }
        return progressService.rollback(progressId);
    }

    @PlatformMutation("Rollback an entire subtree to a timestamp or specific event")
    public SubtreeRollbackResult rollbackSubtree(@PathParam UUID progressId,
                                                 String timestamp, UUID toEventId,
                                                 @ContextParam("tenancyId") String tenancyId) {
        if (timestamp != null && toEventId != null) {
            throw new IllegalArgumentException("timestamp and toEvent are mutually exclusive");
        }
        if (toEventId != null) {
            return subtreeRollbackService.rollbackSubtreeToEvent(progressId, toEventId);
        }
        if (timestamp != null) {
            return subtreeRollbackService.rollbackSubtree(progressId, Instant.parse(timestamp));
        }
        throw new IllegalArgumentException("timestamp or toEvent required");
    }

    @PlatformQuery("Get historical state snapshots for a progress instance")
    public List<ProgressSnapshot> getSnapshots(@PathParam UUID progressId, Integer limit,
                                              @ContextParam("tenancyId") String tenancyId) {
        int effectiveLimit = (limit != null && limit > 0) ? Math.min(limit, 1000) : 100;
        return progressService.getSnapshots(progressId, effectiveLimit);
    }

    @PlatformMutation("Start a named step in a progress instance")
    public ProgressInstance startStep(@PathParam UUID progressId, String stepName,
                                     @ContextParam("tenancyId") String tenancyId) {
        return progressService.startStep(progressId, stepName);
    }

    @PlatformMutation("Complete a named step in a progress instance")
    public ProgressInstance completeStep(@PathParam UUID progressId, String stepName,
                                        @ContextParam("tenancyId") String tenancyId) {
        return progressService.completeStep(progressId, stepName);
    }

    @PlatformMutation("Skip a named step in a progress instance")
    public ProgressInstance skipStep(@PathParam UUID progressId, String stepName,
                                    @ContextParam("tenancyId") String tenancyId) {
        return progressService.skipStep(progressId, stepName);
    }

    @PlatformMutation("Fail a named step in a progress instance")
    public ProgressInstance failStep(@PathParam UUID progressId, String stepName,
                                    @ContextParam("tenancyId") String tenancyId) {
        return progressService.failStep(progressId, stepName);
    }

    @PlatformMutation("Update the data associated with a named step")
    public ProgressInstance updateStepState(@PathParam UUID progressId, String stepName,
                                           JsonNode data,
                                           @ContextParam("tenancyId") String tenancyId) {
        return progressService.updateStepState(progressId, stepName, data);
    }

    @PlatformStream("Real-time progress events for a specific instance")
    public Multi<ProgressUpdatedEvent> streamEvents(@PathParam UUID progressId,
                                                    @ContextParam("tenancyId") String tenancyId) {
        return broadcaster.stream(tenancyId)
                .filter(event -> progressId.equals(event.rootProgressId())
                        || progressId.equals(event.progressId()));
    }

    public record ProgressTreeView(ProgressInstance root, List<ProgressInstance> descendants) {}
}
