package io.casehub.work.federation.rest;

import io.casehub.work.federation.subscription.FederationSubscriptionEntity;
import io.casehub.work.federation.subscription.FederationSubscriptionService;
import io.casehub.work.federation.subscription.SubscriptionFilter;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/federation/subscriptions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FederationSubscriptionResource {

    @Inject
    FederationSubscriptionService subscriptionService;

    @POST
    public Response register(SubscriptionRequest request) {
        byte[] hmacSecret = Base64.getDecoder().decode(request.hmacSecret());
        var filter = new SubscriptionFilter(
                request.filter().candidateGroups(),
                request.filter().candidateUsers(),
                request.tenancyId());

        var entity = subscriptionService.register(
                request.peerId(), request.callbackUrl(),
                request.tenancyId(), filter,
                request.capabilitiesJson(), hmacSecret);

        return Response.status(Response.Status.CREATED)
                .entity(Map.of("id", entity.id, "status", entity.status))
                .build();
    }

    @DELETE
    @Path("/{id}")
    public Response deregister(@PathParam("id") UUID id) {
        FederationSubscriptionEntity sub = FederationSubscriptionEntity.findById(id);
        if (sub == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        sub.status = FederationSubscriptionEntity.SubscriptionStatus.DEREGISTERED;
        return Response.noContent().build();
    }

    @PUT
    @Path("/{id}/reactivate")
    public Response reactivate(@PathParam("id") UUID id) {
        FederationSubscriptionEntity sub = FederationSubscriptionEntity.findById(id);
        if (sub == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        sub.status = FederationSubscriptionEntity.SubscriptionStatus.ACTIVE;
        sub.consecutiveFailures = 0;
        return Response.ok(Map.of("id", sub.id, "status", sub.status)).build();
    }

    public record SubscriptionRequest(
            String peerId,
            String callbackUrl,
            String tenancyId,
            FilterRequest filter,
            String capabilitiesJson,
            String hmacSecret
    ) {}

    public record FilterRequest(
            List<String> candidateGroups,
            List<String> candidateUsers
    ) {}
}
