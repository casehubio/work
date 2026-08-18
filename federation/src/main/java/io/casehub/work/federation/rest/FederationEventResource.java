package io.casehub.work.federation.rest;

import io.casehub.work.federation.FederationReceiver;
import io.casehub.work.federation.subscription.FederationSubscriptionEntity;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

@Path("/federation/events")
public class FederationEventResource {

    @Inject
    FederationReceiver receiver;

    @POST
    @Consumes("application/cloudevents+json")
    public Response receiveEvent(String cloudEventJson,
                                  @HeaderParam("X-Federation-Signature") String signature,
                                  @HeaderParam("X-Federation-Peer-Id") String peerId) {
        if (signature == null || signature.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("Missing X-Federation-Signature header").build();
        }

        var subscriptions = FederationSubscriptionEntity.<FederationSubscriptionEntity>find(
                "peerId = ?1 and status = ?2",
                peerId, FederationSubscriptionEntity.SubscriptionStatus.ACTIVE).list();

        if (subscriptions.isEmpty()) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("No active subscription for peer: " + peerId).build();
        }

        byte[] hmacSecret = subscriptions.getFirst().hmacSecretEncrypted;
        receiver.onEvent(cloudEventJson, signature, hmacSecret);
        return Response.accepted().build();
    }
}
