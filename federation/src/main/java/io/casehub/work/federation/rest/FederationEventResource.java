package io.casehub.work.federation.rest;

import io.casehub.work.federation.FederationReceiver;
import io.casehub.work.federation.subscription.FederationSubscriptionEntity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import io.casehub.platform.api.mcp.HandWrittenEndpoint;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

@Path("/federation/events")
@HandWrittenEndpoint("Inbound CloudEvents webhook with HMAC verification — not a domain API")
public class FederationEventResource {

    @Inject
    EntityManager em;

    @Inject
    FederationReceiver receiver;

    @POST
    @Consumes("application/cloudevents+json")
    public Response receiveEvent(String cloudEventJson,
                                 @HeaderParam("X-Federation-Signature") String signature,
                                 @HeaderParam("X-Federation-Peer-Id") String peerId) {
        if (peerId == null || peerId.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity("Missing X-Federation-Peer-Id header").build();
        }
        if (signature == null || signature.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                           .entity("Missing X-Federation-Signature header").build();
        }

        var subscriptions = em.createQuery("FROM FederationSubscriptionEntity WHERE peerId = ?1 AND status = ?2", FederationSubscriptionEntity.class)
                .setParameter(1, peerId).setParameter(2, FederationSubscriptionEntity.SubscriptionStatus.ACTIVE).getResultList();

        if (subscriptions.isEmpty()) {
            return Response.status(Response.Status.FORBIDDEN)
                           .entity("No active subscription for peer: " + peerId).build();
        }

        byte[] hmacSecret = subscriptions.getFirst().hmacSecretEncrypted;
        try {
            receiver.onEvent(cloudEventJson, signature, hmacSecret);
            return Response.accepted().build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }
}
