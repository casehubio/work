package io.casehub.work.federation;

import io.casehub.work.api.WorkItem;
import io.casehub.work.api.spi.WorkItemStore;
import io.casehub.work.client.WorkItemClient;
import io.casehub.work.client.WorkItemClient.ClientResponse;
import io.casehub.work.federation.subscription.FederationSubscriptionEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.time.Duration;

@ApplicationScoped
public class FederationProxy {

    private static final Logger LOG = Logger.getLogger(FederationProxy.class);

    @Inject
    FederationConfig config;

    @Inject
    WorkItemStore workItemStore;

    private WorkItemClient client() {
        return ClientHolder.get(config);
    }

    public WorkItem claim(WorkItem shadow, String claimantId) {
        String         ownerUrl = resolveOwnerBaseUrl(shadow);
        ClientResponse response = client().claim(ownerUrl, shadow.originWorkItemId().toString(), claimantId, null);
        return handleResponse(shadow, response, "claim");
    }

    public WorkItem complete(WorkItem shadow, String actorId, String resolution, String outcome) {
        String ownerUrl = resolveOwnerBaseUrl(shadow);
        ClientResponse response = client().complete(ownerUrl, shadow.originWorkItemId().toString(),
                                                    actorId, resolution, outcome, null);
        return handleResponse(shadow, response, "complete");
    }

    public WorkItem reject(WorkItem shadow, String actorId, String reason, String outcome) {
        String ownerUrl = resolveOwnerBaseUrl(shadow);
        ClientResponse response = client().reject(ownerUrl, shadow.originWorkItemId().toString(),
                                                  actorId, reason, outcome, null);
        return handleResponse(shadow, response, "reject");
    }

    public WorkItem delegate(WorkItem shadow, String actorId, String toAssigneeId) {
        String ownerUrl = resolveOwnerBaseUrl(shadow);
        ClientResponse response = client().delegate(ownerUrl, shadow.originWorkItemId().toString(),
                                                    actorId, toAssigneeId, null);
        return handleResponse(shadow, response, "delegate");
    }

    public WorkItem release(WorkItem shadow, String actorId) {
        String ownerUrl = resolveOwnerBaseUrl(shadow);
        ClientResponse response = client().release(ownerUrl, shadow.originWorkItemId().toString(),
                                                   actorId, null);
        return handleResponse(shadow, response, "release");
    }

    private WorkItem handleResponse(WorkItem shadow, ClientResponse response, String operation) {
        if (response.isSuccess()) {
            LOG.debugf("Federation proxy %s succeeded for shadow %s -> owner %s",
                       operation, shadow.id(), shadow.originWorkItemId());
            return shadow;
        }
        if (response.isConflict()) {
            throw new WebApplicationException("Conflict — another actor may have already performed this operation",
                                              Response.Status.CONFLICT);
        }
        throw new WebApplicationException("Owning service temporarily unreachable",
                                          Response.Status.SERVICE_UNAVAILABLE);
    }

    private String resolveOwnerBaseUrl(WorkItem shadow) {
        var subscriptions = FederationSubscriptionEntity.<FederationSubscriptionEntity>find(
                                                                "tenancyId = ?1 and status = ?2",
                                                                shadow.tenancyId(), FederationSubscriptionEntity.SubscriptionStatus.ACTIVE)
                                                        .list();
        return subscriptions.stream()
                            .filter(s -> s.peerId.equals(shadow.originServiceId()))
                            .map(s -> s.baseUrl)
                            .findFirst()
                            .orElseThrow(() -> new WebApplicationException(
                                    "No active subscription for origin service: " + shadow.originServiceId(),
                                    Response.Status.BAD_GATEWAY));
    }

    private static class ClientHolder {
        private static volatile WorkItemClient instance;

        static synchronized WorkItemClient get(FederationConfig config) {
            if (instance == null) {
                instance = new WorkItemClient(Duration.ofSeconds(config.proxyTimeoutSeconds()));
            }
            return instance;
        }
    }
}
