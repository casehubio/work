package io.casehub.work.federation.subscription;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.work.api.WorkItem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class FederationSubscriptionService {

    @Inject
    EntityManager em;

    @Inject
    ObjectMapper objectMapper;

    @Transactional
    public FederationSubscriptionEntity register(String peerId, String callbackUrl,
                                                  String baseUrl, String tenancyId,
                                                  SubscriptionFilter filter,
                                                  String capabilitiesJson, byte[] hmacSecret) {
        var entity = new FederationSubscriptionEntity();
        entity.id = UUID.randomUUID();
        entity.peerId = peerId;
        entity.callbackUrl = callbackUrl;
        entity.baseUrl = baseUrl;
        entity.tenancyId = tenancyId;
        entity.filterJson = serializeFilter(filter);
        entity.capabilitiesJson = capabilitiesJson;
        entity.hmacSecretEncrypted = hmacSecret;
        entity.status = FederationSubscriptionEntity.SubscriptionStatus.ACTIVE;
        entity.consecutiveFailures = 0;
        entity.createdAt = Instant.now();
        em.persist(entity);
        em.flush();
        return entity;
    }

    public List<FederationSubscriptionEntity> findActiveSubscriptions(String tenancyId) {
        return em.createQuery("FROM FederationSubscriptionEntity WHERE tenancyId = ?1 AND status = ?2", FederationSubscriptionEntity.class)
                .setParameter(1, tenancyId).setParameter(2, FederationSubscriptionEntity.SubscriptionStatus.ACTIVE)
                .getResultList();
    }

    public List<FederationSubscriptionEntity> matchSubscriptions(WorkItem workItem) {
        List<FederationSubscriptionEntity> active = findActiveSubscriptions(workItem.tenancyId());
        return active.stream()
                .filter(sub -> {
                    SubscriptionFilter filter = deserializeFilter(sub.filterJson);
                    return SubscriptionFilterEvaluator.matches(filter, workItem);
                })
                .toList();
    }

    @Transactional
    public void lockOn(UUID subscriptionId, UUID workItemId) {
        var tracking = new FederationTrackingEntity();
        tracking.subscriptionId = subscriptionId;
        tracking.workItemId = workItemId;
        em.persist(tracking);
        em.flush();
    }

    public List<FederationSubscriptionEntity> findLockedSubscriptions(UUID workItemId) {
        List<FederationTrackingEntity> trackings = em.createQuery("FROM FederationTrackingEntity WHERE workItemId = ?1", FederationTrackingEntity.class)
                .setParameter(1, workItemId).getResultList();
        List<UUID> subscriptionIds = trackings.stream()
                .map(t -> t.subscriptionId).toList();
        if (subscriptionIds.isEmpty()) {
            return List.of();
        }
        return em.createQuery("FROM FederationSubscriptionEntity WHERE id IN ?1 AND status = ?2", FederationSubscriptionEntity.class)
                .setParameter(1, subscriptionIds).setParameter(2, FederationSubscriptionEntity.SubscriptionStatus.ACTIVE)
                .getResultList();
    }

    @Transactional
    public void removeTracking(UUID workItemId) {
        em.createQuery("DELETE FROM FederationTrackingEntity WHERE workItemId = ?1")
                .setParameter(1, workItemId).executeUpdate();
    }

    @Transactional
    public void recordSuccess(UUID subscriptionId) {
        FederationSubscriptionEntity sub = em.find(FederationSubscriptionEntity.class, subscriptionId);
        if (sub != null) {
            sub.consecutiveFailures = 0;
        }
    }

    @Transactional
    public void recordFailure(UUID subscriptionId) {
        FederationSubscriptionEntity sub = em.find(FederationSubscriptionEntity.class, subscriptionId);
        if (sub != null) {
            sub.consecutiveFailures++;
            sub.lastFailureAt = Instant.now();
            if (sub.consecutiveFailures >= 5) {
                sub.status = FederationSubscriptionEntity.SubscriptionStatus.SUSPENDED;
            }
        }
    }

    private String serializeFilter(SubscriptionFilter filter) {
        try {
            return objectMapper.writeValueAsString(filter);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize subscription filter", e);
        }
    }

    SubscriptionFilter deserializeFilter(String json) {
        try {
            return objectMapper.readValue(json, SubscriptionFilter.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize subscription filter", e);
        }
    }
}
