package io.casehub.work.queues.api;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.mcp.HandWrittenEndpoint;
import io.casehub.work.queues.event.WorkItemQueueEvent;
import io.casehub.work.queues.service.WorkItemQueueEventBroadcaster;
import io.smallrye.mutiny.Multi;

@Path("/api/work/queues")
@ApplicationScoped
@HandWrittenEndpoint("SSE streaming — generator does not support Multi<> return types for queue events")
public class QueueSseResource {

    @Inject
    WorkItemQueueEventBroadcaster broadcaster;

    @Inject
    CurrentPrincipal currentPrincipal;

    @GET
    @Path("/stream-queue-events/{queueViewId}")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @org.jboss.resteasy.reactive.RestStreamElementType(MediaType.APPLICATION_JSON)
    public Multi<WorkItemQueueEvent> streamQueueEvents(@jakarta.ws.rs.PathParam("queueViewId") UUID queueViewId) {
        return broadcaster.stream(queueViewId, currentPrincipal.tenancyId());
    }
}
