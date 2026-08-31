package io.casehub.work.progress.rest;

import io.casehub.work.progress.ProgressCreateRequest;
import io.casehub.work.progress.ProgressInstance;
import io.casehub.work.progress.ProgressSnapshot;
import io.casehub.work.progress.ProgressUpdatedEvent;
import io.casehub.work.progress.SubtreeRollbackResult;
import io.casehub.work.progress.runtime.service.SubtreeRollbackService;
import io.casehub.work.progress.runtime.event.ProgressEventBroadcaster;
import io.casehub.work.progress.runtime.service.ProgressService;
import io.casehub.work.progress.spi.ProgressEventStore;
import io.casehub.work.progress.spi.ProgressInstanceStore;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestStreamElementType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Path("/progress")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProgressResource {

    @Inject
    ProgressService progressService;

    @Inject
    ProgressEventStore eventStore;

    @Inject
    ProgressInstanceStore instanceStore;

    @Inject
    ProgressEventBroadcaster broadcaster;
    @Inject
    SubtreeRollbackService   subtreeRollbackService;


    @POST
    public Response create(CreateProgressRequest request) {
        ProgressCreateRequest domainReq = new ProgressCreateRequest(
                request.tenancyId(), request.scopeType(), request.scopeId(),
                request.shapeType(), request.state(),
                request.parentProgressId(), request.rollupStrategyId(),
                request.definition(), request.rollbackPolicy(),
                request.visualisationMode(), null);
        ProgressInstance instance = progressService.create(domainReq);
        return Response.status(Response.Status.CREATED).entity(instance).build();}

    @PUT
    @Path("/{id}/state")
    public Response updateState(@PathParam("id") UUID id, UpdateStateRequest body) {
        ProgressInstance updated = progressService.updateState(id, body.state());
        return Response.ok(updated).build();
    }

    @POST
    @Path("/{id}/complete")
    public Response complete(@PathParam("id") UUID id) {
        ProgressInstance completed = progressService.complete(id);
        return Response.ok(completed).build();
    }

    @POST
    @Path("/{id}/fail")
    public Response fail(@PathParam("id") UUID id) {
        ProgressInstance failed = progressService.fail(id);
        return Response.ok(failed).build();
    }

    @POST
    @Path("/{id}/reactivate")
    public Response reactivate(@PathParam("id") UUID id) {
        ProgressInstance reactivated = progressService.reactivate(id);
        return Response.ok(reactivated).build();
    }

    @POST
    @Path("/{id}/children")
    public Response attachChild(@PathParam("id") UUID parentId, CreateProgressRequest request) {
        ProgressCreateRequest domainReq = new ProgressCreateRequest(
                request.tenancyId(), request.scopeType(), request.scopeId(),
                request.shapeType(), request.state(),
                null, request.rollupStrategyId(), request.definition(),
                request.rollbackPolicy(), request.visualisationMode(), null);
        ProgressInstance child = progressService.attachChild(parentId, domainReq);
        return Response.status(Response.Status.CREATED).entity(child).build();}

    @GET
    @Path("/{id}")
    public Response getById(@PathParam("id") UUID id) {
        return progressService.findById(id)
                .map(inst -> Response.ok(inst).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @GET
    @Path("/{id}/tree")
    public Response getTree(@PathParam("id") UUID id) {
        return progressService.findById(id)
                              .map(root -> {
                                  List<ProgressInstance> descendants = instanceStore.findDescendantsOf(id);
                                  return Response.ok(new TreeResponse(root, descendants)).build();
                              })
                              .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @GET
    public List<ProgressInstance> findByScope(
            @QueryParam("scopeType") String scopeType,
            @QueryParam("scopeId") String scopeId) {
        return progressService.findByScope(scopeType, scopeId);
    }

    @GET
    @Path("/{id}/events")
    public List<ProgressUpdatedEvent> getEvents(
            @PathParam("id") UUID id,
            @QueryParam("since") String since) {
        if (since != null) {
            return eventStore.findByProgressIdSince(id, Instant.parse(since));
        }
        return eventStore.findByProgressId(id);
    }

    @POST
    @Path("/{id}/rollback")
    public Response rollback(@PathParam("id") UUID id, @QueryParam("toEvent") UUID toEventId) {
        ProgressInstance result;
        if (toEventId != null) {
            result = progressService.rollbackToEvent(id, toEventId);
        } else {
            result = progressService.rollback(id);
        }
        return Response.ok(result).build();
    }

    @POST
    @Path("/{id}/rollback/subtree")
    public Response rollbackSubtree(
            @PathParam("id") UUID id,
            @QueryParam("timestamp") String timestamp,
            @QueryParam("toEvent") UUID toEventId) {
        if (timestamp != null && toEventId != null) {
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity("timestamp and toEvent are mutually exclusive").build();
        }
        SubtreeRollbackResult result;
        if (toEventId != null) {
            result = subtreeRollbackService.rollbackSubtreeToEvent(id, toEventId);
        } else if (timestamp != null) {
            result = subtreeRollbackService.rollbackSubtree(id, Instant.parse(timestamp));
        } else {
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity("timestamp or toEvent required").build();
        }
        return Response.ok(result).build();
    }


    @GET
    @Path("/{id}/snapshots")
    public List<ProgressSnapshot> getSnapshots(
            @PathParam("id") UUID id,
            @QueryParam("limit") Integer limit) {
        int effectiveLimit = (limit != null && limit > 0) ? Math.min(limit, 1000) : 100;
        return progressService.getSnapshots(id, effectiveLimit);
    }


    @GET
    @Path("/{id}/stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public Multi<ProgressUpdatedEvent> streamEvents(
            @PathParam("id") UUID id,
            @QueryParam("tenancyId") String tenancyId) {
        return broadcaster.stream(tenancyId)
                .filter(event -> id.equals(event.rootProgressId())
                        || id.equals(event.progressId()));
    }

    // --- Step convenience endpoints ---

    @POST
    @Path("/{id}/steps/{stepName}/start")
    public Response startStep(@PathParam("id") UUID id, @PathParam("stepName") String stepName) {
        ProgressInstance updated = progressService.startStep(id, stepName);
        return Response.ok(updated).build();
    }

    @POST
    @Path("/{id}/steps/{stepName}/complete")
    public Response completeStep(@PathParam("id") UUID id, @PathParam("stepName") String stepName) {
        ProgressInstance updated = progressService.completeStep(id, stepName);
        return Response.ok(updated).build();
    }

    @POST
    @Path("/{id}/steps/{stepName}/skip")
    public Response skipStep(@PathParam("id") UUID id, @PathParam("stepName") String stepName) {
        ProgressInstance updated = progressService.skipStep(id, stepName);
        return Response.ok(updated).build();
    }

    @POST
    @Path("/{id}/steps/{stepName}/fail")
    public Response failStep(@PathParam("id") UUID id, @PathParam("stepName") String stepName) {
        ProgressInstance updated = progressService.failStep(id, stepName);
        return Response.ok(updated).build();
    }

    @PUT
    @Path("/{id}/steps/{stepName}/state")
    public Response updateStepState(@PathParam("id") UUID id,
                                    @PathParam("stepName") String stepName,
                                    UpdateStepDataRequest body) {
        ProgressInstance updated = progressService.updateStepState(id, stepName, body.data());
        return Response.ok(updated).build();
    }

    public record TreeResponse(ProgressInstance root, List<ProgressInstance> descendants) {}
}
