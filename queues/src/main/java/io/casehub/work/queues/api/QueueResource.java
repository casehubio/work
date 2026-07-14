package io.casehub.work.queues.api;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.reactive.RestStreamElementType;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.work.queues.event.WorkItemQueueEvent;
import io.casehub.work.queues.model.QueueView;
import io.casehub.work.queues.repository.QueueSnapshotStore;
import io.casehub.work.queues.repository.QueueViewStore;
import io.casehub.work.queues.service.QueueMembershipService;
import io.casehub.work.queues.service.WorkItemQueueEventBroadcaster;
import io.casehub.work.rest.WorkItemMapper;
import io.casehub.work.rest.WorkItemResponse;
import io.casehub.work.runtime.service.WorkItemSummaryBuilder;
import io.smallrye.mutiny.Multi;

/** REST resource for managing queue views and querying their live content. */
@Path("/queues")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class QueueResource {

    @Inject
    QueueViewStore queueViewStore;

    @Inject
    WorkItemQueueEventBroadcaster queueEventBroadcaster;

    @Inject
    QueueMembershipService membershipService;

    @Inject
    QueueSnapshotStore snapshotStore;

    @Inject
    CurrentPrincipal currentPrincipal;

    /**
     * Request body for creating a new queue view.
     *
     * @param name display name
     * @param labelPattern label pattern used to populate the queue
     * @param scope visibility scope as a Path string (null/blank = root)
     * @param additionalConditions optional extra filter conditions
     * @param sortField field to sort results by
     * @param sortDirection sort direction (ASC or DESC)
     */
    public record CreateQueueRequest(String name, String labelPattern, String scope,
            String additionalConditions, String sortField, String sortDirection) {
    }

    /**
     * List all queue views.
     *
     * @return list of all queue views (id, name, labelPattern, scope)
     */
    @GET
    @Transactional
    public List<Map<String, Object>> list() {
        return queueViewStore.scanAll().stream()
                .map(q -> Map.<String, Object> of(
                        "id", q.id, "name", q.name, "labelPattern", q.labelPattern, "scope", q.scope.value()))
                .toList();
    }

    /**
     * Create a new queue view.
     *
     * @param req the queue view definition
     * @return 201 Created with the new queue view id and name, or 400 if labelPattern is missing
     */
    @POST
    @Transactional
    public Response create(final CreateQueueRequest req) {
        if (req.labelPattern() == null || req.labelPattern().isBlank()) {
            return Response.status(400).entity(Map.of("error", "labelPattern is required")).build();
        }
        final QueueView q = new QueueView();
        q.name = req.name();
        q.labelPattern = req.labelPattern();
        final io.casehub.platform.api.path.Path scopePath;
        try {
            scopePath = (req.scope() == null || req.scope().isBlank())
                    ? io.casehub.platform.api.path.Path.root()
                    : io.casehub.platform.api.path.Path.parse(req.scope());
        } catch (IllegalArgumentException e) {
            return Response.status(400)
                    .entity(Map.of("error", "invalid scope: " + e.getMessage())).build();
        }
        q.scope = scopePath;
        q.additionalConditions = req.additionalConditions();
        q.sortField = req.sortField() != null ? req.sortField() : "createdAt";
        q.sortDirection = req.sortDirection() != null ? req.sortDirection() : "ASC";
        queueViewStore.put(q);
        return Response.status(201)
                .entity(Map.of("id", q.id, "name", q.name, "labelPattern", q.labelPattern)).build();
    }

    /**
     * Query the live content of a queue view — returns all WorkItems matching its label pattern,
     * further filtered by {@code additionalConditions} (JEXL expression) if set, and sorted by
     * {@code sortField}/{@code sortDirection}.
     *
     * @param id the queue view UUID
     * @return 200 with the filtered, sorted list of matching WorkItems, or 404 if not found
     */
    @GET
    @Path("/{id}")
    @Transactional
    public Response query(@PathParam("id") final UUID id) {
        final QueueView q = queueViewStore.get(id).orElse(null);
        if (q == null) {
            return Response.status(404).entity(Map.of("error", "Queue view not found")).build();
        }
        final var candidates = membershipService.evaluateMembers(q);

        final var items = candidates.stream()
                .map(WorkItemMapper::toResponse)
                .sorted(buildComparator(q.sortField, q.sortDirection))
                .toList();
        return Response.ok(items).build();
    }

    private java.util.Comparator<WorkItemResponse> buildComparator(
            final String sortField, final String sortDirection) {
        final boolean asc = !"DESC".equalsIgnoreCase(sortDirection);
        final java.util.Comparator<WorkItemResponse> base = switch (sortField != null ? sortField : "createdAt") {
            case "priority" -> java.util.Comparator.comparing(
                    r -> r.priority() != null ? r.priority().name() : "");
            case "title" -> java.util.Comparator.comparing(
                    r -> r.title() != null ? r.title() : "");
            default -> // createdAt
                java.util.Comparator.comparing(
                        r -> r.createdAt() != null ? r.createdAt() : java.time.Instant.EPOCH);
        };
        return asc ? base : base.reversed();
    }

    /**
     * Delete a queue view by id.
     *
     * @param id the queue view UUID
     * @return 204 No Content on success, or 404 if the queue view is not found
     */
    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("id") final UUID id) {
        if (!queueViewStore.delete(id)) {
            return Response.status(404).entity(Map.of("error", "Not found")).build();
        }
        return Response.noContent().build();
    }

    @GET
    @Path("/{id}/summary")
    @Transactional
    public Response summary(@PathParam("id") final UUID id) {
        final QueueView q = queueViewStore.get(id).orElse(null);
        if (q == null) {
            return Response.status(404).entity(Map.of("error", "Queue view not found")).build();
        }
        final var members = membershipService.evaluateMembers(q);
        return Response.ok(WorkItemSummaryBuilder.build(members, Instant.now())).build();
    }

    @GET
    @Path("/{id}/trend")
    @Transactional
    public Response trend(@PathParam("id") final UUID id,
                          @jakarta.ws.rs.QueryParam("period") final String periodParam) {
        final QueueView q = queueViewStore.get(id).orElse(null);
        if (q == null) {
            return Response.status(404)
                    .entity(Map.of("error", "Queue view not found")).build();
        }
        final Duration period;
        try {
            period = parsePeriod(periodParam != null ? periodParam : "24h");
        } catch (final Exception e) {
            return Response.status(400)
                    .entity(Map.of("error", "Invalid period: " + periodParam)).build();
        }
        final Instant now = Instant.now();
        final Instant from = now.minus(period);
        final var snapshots = snapshotStore.findByQueueAndPeriod(q.id, from, now);
        final var dataPoints = snapshots.stream()
                .map(s -> new QueueTrendResponse.DataPoint(s.snapshotAt, s.memberCount))
                .toList();
        return Response.ok(new QueueTrendResponse(
                q.id, q.name, period.toString(), dataPoints)).build();
    }

    static Duration parsePeriod(final String input) {
        if (input.startsWith("P") || input.startsWith("p")) {
            return Duration.parse(input);
        }
        final String normalized = input.toLowerCase();
        if (normalized.endsWith("h")) {
            return Duration.ofHours(
                    Long.parseLong(normalized.substring(0, normalized.length() - 1)));
        }
        if (normalized.endsWith("d")) {
            return Duration.ofDays(
                    Long.parseLong(normalized.substring(0, normalized.length() - 1)));
        }
        return Duration.parse("PT" + input);
    }

    /**
     * Server-Sent Events stream of {@link WorkItemQueueEvent} (ADDED/REMOVED/CHANGED)
     * scoped to a specific queue view.
     *
     * <p>
     * Hot stream — only events after the client connects are delivered.
     * Use {@code GET /queues/{id}/workitems} to fetch the current queue contents.
     *
     * @param queueViewId the queue view UUID
     * @return SSE stream of queue membership events for this queue
     */
    @GET
    @Path("/{id}/events")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public Multi<WorkItemQueueEvent> streamQueueEvents(@PathParam("id") final UUID queueViewId) {
        return queueEventBroadcaster.stream(queueViewId, currentPrincipal.tenancyId());
    }
}
