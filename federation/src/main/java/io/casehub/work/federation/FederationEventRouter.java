package io.casehub.work.federation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.work.api.WorkItem;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.federation.subscription.FederationSubscriptionEntity;
import io.casehub.work.federation.subscription.FederationSubscriptionService;
import io.casehub.work.federation.transport.FederationTransport;
import io.casehub.work.runtime.event.WorkItemLifecycleEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class FederationEventRouter {

    private static final Logger LOG = Logger.getLogger(FederationEventRouter.class);
    private static final String EVENT_TYPE_PREFIX = "io.casehub.work.federation.";

    @Inject
    FederationSubscriptionService subscriptionService;

    @Inject
    FederationTransport transport;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    FederationConfig config;

    public void onWorkItemLifecycle(
            @Observes(during = TransactionPhase.AFTER_SUCCESS) WorkItemLifecycleEvent event) {

        WorkItem workItem = event.workItem();
        if (workItem == null) {
            return;
        }

        if (workItem.originServiceId() != null) {
            return;
        }

        String eventType = mapEventType(event.type());
        List<FederationSubscriptionEntity> subscribers;

        if (isCreationEvent(event.type())) {
            subscribers = subscriptionService.matchSubscriptions(workItem);
            for (var sub : subscribers) {
                subscriptionService.lockOn(sub.id, workItem.id());
            }
        } else {
            subscribers = subscriptionService.findLockedSubscriptions(workItem.id());
        }

        if (subscribers.isEmpty()) {
            return;
        }

        String cloudEventJson = buildCloudEvent(eventType, workItem, event.type());

        for (var sub : subscribers) {
            try {
                transport.send(cloudEventJson, sub.callbackUrl, sub.hmacSecretEncrypted);
                subscriptionService.recordSuccess(sub.id);
            } catch (Exception e) {
                LOG.warnf(e, "Federation delivery failed for subscription %s to %s",
                        sub.id, sub.callbackUrl);
                subscriptionService.recordFailure(sub.id);
            }
        }

        if (isTerminal(workItem.status())) {
            subscriptionService.removeTracking(workItem.id());
        }
    }

    private String buildCloudEvent(String eventType, WorkItem wi, String rawType) {
        try {
            ObjectNode ce = objectMapper.createObjectNode();
            ce.put("specversion", "1.0");
            ce.put("type", eventType);
            ce.put("source", "urn:casehub:work:" + config.serviceId());
            ce.put("id", java.util.UUID.randomUUID().toString());
            ce.put("time", Instant.now().toString());
            ce.put("tenancyid", wi.tenancyId());
            ce.put("workitemversion", wi.version());

            ObjectNode data = ce.putObject("data");
            data.put("id", wi.id().toString());
            data.put("title", wi.title());
            data.put("description", wi.description());
            data.put("tenancyId", wi.tenancyId());
            data.put("status", wi.status() != null ? wi.status().name() : null);
            data.put("priority", wi.priority() != null ? wi.priority().name() : null);
            data.put("assigneeId", wi.assigneeId());
            data.put("owner", wi.owner());
            data.put("candidateGroups", wi.candidateGroups());
            data.put("candidateUsers", wi.candidateUsers());
            data.put("createdBy", wi.createdBy());
            data.put("callerRef", wi.callerRef());
            data.put("payload", wi.payload());
            data.put("resolution", wi.resolution());
            data.put("outcome", wi.outcome());
            data.put("scope", wi.scope());
            if (wi.expiresAt() != null) data.put("expiresAt", wi.expiresAt().toString());
            if (wi.claimDeadline() != null) data.put("claimDeadline", wi.claimDeadline().toString());
            if (wi.createdAt() != null) data.put("createdAt", wi.createdAt().toString());
            if (wi.updatedAt() != null) data.put("updatedAt", wi.updatedAt().toString());
            if (wi.assignedAt() != null) data.put("assignedAt", wi.assignedAt().toString());
            if (wi.startedAt() != null) data.put("startedAt", wi.startedAt().toString());
            if (wi.completedAt() != null) data.put("completedAt", wi.completedAt().toString());

            return objectMapper.writeValueAsString(ce);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build federation CloudEvent", e);
        }
    }

    private static String mapEventType(String rawType) {
        if (rawType == null) return EVENT_TYPE_PREFIX + "unknown";
        String suffix = rawType.contains(".") ? rawType.substring(rawType.lastIndexOf('.') + 1) : rawType.toLowerCase();
        return EVENT_TYPE_PREFIX + suffix;
    }

    private static boolean isCreationEvent(String type) {
        return type != null && type.endsWith(".created");
    }

    private static boolean isTerminal(WorkItemStatus status) {
        return status != null && WorkItemStatus.TERMINAL_STATUSES.contains(status);
    }
}
