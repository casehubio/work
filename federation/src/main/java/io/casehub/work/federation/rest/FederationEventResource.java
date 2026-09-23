package io.casehub.work.federation.rest;

import io.casehub.platform.api.mcp.HeaderParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformWebhook;
import io.casehub.platform.api.mcp.RestPath;
import io.casehub.work.federation.FederationReceiver;
import io.casehub.work.federation.subscription.FederationSubscriptionEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

@McpDomain(value = "work/federation", basePath = "/federation")
@ApplicationScoped
public class FederationEventResource {

    @Inject
    FederationReceiver receiver;

    @PlatformWebhook(value = "Receive federation CloudEvents", consumes = "application/cloudevents+json")
    @RestPath("/events")
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

        var subscriptions = FederationSubscriptionEntity.<FederationSubscriptionEntity>find(
                "peerId = ?1 and status = ?2",
                peerId, FederationSubscriptionEntity.SubscriptionStatus.ACTIVE).list();

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
