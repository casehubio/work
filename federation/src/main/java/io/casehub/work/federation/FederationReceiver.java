package io.casehub.work.federation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.work.api.WorkItem;
import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.api.spi.WorkItemStore;
import io.casehub.work.federation.subscription.FederationSubscriptionService;
import io.casehub.work.federation.transport.HmacSigner;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class FederationReceiver {

    private static final Logger LOG = Logger.getLogger(FederationReceiver.class);

    @Inject
    WorkItemStore workItemStore;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    FederationSubscriptionService subscriptionService;

    @Transactional
    public void onEvent(String cloudEventJson, String signature, byte[] hmacSecret) {
        if (!HmacSigner.verify(cloudEventJson, signature, hmacSecret)) {
            LOG.warn("Federation event rejected — HMAC verification failed");
            return;
        }

        try {
            JsonNode ce = objectMapper.readTree(cloudEventJson);
            String type = ce.path("type").asText();
            String sourceServiceId = extractServiceId(ce.path("source").asText());
            String tenancyId = ce.path("tenancyid").asText(null);
            long incomingVersion = ce.path("workitemversion").asLong(0);
            JsonNode data = ce.path("data");

            if (tenancyId == null || tenancyId.isEmpty()) {
                LOG.warn("Federation event rejected — missing tenancyid extension");
                return;
            }

            WorkItem projection = parseProjection(data, sourceServiceId, incomingVersion);

            Optional<WorkItem> existingShadow = workItemStore.findByOrigin(
                    sourceServiceId, projection.originWorkItemId());

            if (existingShadow.isPresent()) {
                WorkItem shadow = existingShadow.get();
                if (shadow.originVersion() != null && incomingVersion <= shadow.originVersion()) {
                    LOG.debugf("Stale federation event discarded: incoming v%d <= shadow v%d for %s",
                            incomingVersion, shadow.originVersion(), shadow.originWorkItemId());
                    return;
                }
                projection = projection.toBuilder().id(shadow.id()).build();
            } else {
                projection = projection.toBuilder().id(UUID.randomUUID()).build();
            }

            String namespacedCallerRef = projection.callerRef() != null
                    ? "federation:" + sourceServiceId + ":" + projection.callerRef()
                    : null;
            projection = projection.toBuilder().callerRef(namespacedCallerRef).build();

            try (var ctx = FederationSyncContext.activate()) {
                workItemStore.put(projection);
            }

            if (isTerminal(type)) {
                subscriptionService.removeTracking(projection.originWorkItemId());
            }

            LOG.debugf("Federation event processed: %s for %s (v%d)",
                    type, projection.originWorkItemId(), incomingVersion);

        } catch (Exception e) {
            LOG.errorf(e, "Failed to process federation event");
        }
    }

    private WorkItem parseProjection(JsonNode data, String sourceServiceId, long version) {
        return WorkItem.builder()
                .title(data.path("title").asText(null))
                .description(data.path("description").asText(null))
                .tenancyId(data.path("tenancyId").asText(null))
                .status(parseEnum(data, "status", WorkItemStatus.class))
                .priority(parseEnum(data, "priority", WorkItemPriority.class))
                .assigneeId(data.path("assigneeId").asText(null))
                .owner(data.path("owner").asText(null))
                .candidateGroups(data.path("candidateGroups").asText(null))
                .candidateUsers(data.path("candidateUsers").asText(null))
                .createdBy(data.path("createdBy").asText(null))
                .callerRef(data.path("callerRef").asText(null))
                .payload(data.path("payload").asText(null))
                .resolution(data.path("resolution").asText(null))
                .expiresAt(parseInstant(data, "expiresAt"))
                .claimDeadline(parseInstant(data, "claimDeadline"))
                .createdAt(parseInstant(data, "createdAt"))
                .updatedAt(parseInstant(data, "updatedAt"))
                .assignedAt(parseInstant(data, "assignedAt"))
                .startedAt(parseInstant(data, "startedAt"))
                .completedAt(parseInstant(data, "completedAt"))
                .outcome(data.path("outcome").asText(null))
                .scope(data.path("scope").asText(null))
                .originServiceId(sourceServiceId)
                .originWorkItemId(UUID.fromString(data.path("id").asText()))
                .originVersion(version)
                .build();
    }

    private static String extractServiceId(String source) {
        if (source != null && source.startsWith("urn:casehub:work:")) {
            return source.substring("urn:casehub:work:".length());
        }
        return source;
    }

    private static boolean isTerminal(String eventType) {
        return eventType != null && (
                eventType.endsWith(".completed") ||
                eventType.endsWith(".rejected") ||
                eventType.endsWith(".cancelled") ||
                eventType.endsWith(".expired") ||
                eventType.endsWith(".obsoleted") ||
                eventType.endsWith(".faulted"));
    }

    private static <E extends Enum<E>> E parseEnum(JsonNode data, String field, Class<E> type) {
        String value = data.path(field).asText(null);
        if (value == null) return null;
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Instant parseInstant(JsonNode data, String field) {
        String value = data.path(field).asText(null);
        if (value == null) return null;
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            return null;
        }
    }
}
