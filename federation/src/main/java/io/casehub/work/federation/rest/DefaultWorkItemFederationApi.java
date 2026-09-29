package io.casehub.work.federation.rest;

import java.util.Base64;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.work.api.spi.WorkItemFederationApi;
import io.casehub.work.api.view.FederationSubscriptionRequest;
import io.casehub.work.api.view.FederationSubscriptionResult;
import io.casehub.work.federation.subscription.FederationSubscriptionEntity;
import io.casehub.work.federation.subscription.FederationSubscriptionService;
import io.casehub.work.federation.subscription.SubscriptionFilter;

@ApplicationScoped
public class DefaultWorkItemFederationApi implements WorkItemFederationApi {

    @Inject
    FederationSubscriptionService subscriptionService;

    @Override
    public FederationSubscriptionResult register(FederationSubscriptionRequest request, String tenancyId) {
        byte[] hmacSecret = Base64.getDecoder().decode(request.hmacSecret());
        var filter = new SubscriptionFilter(
                request.filter().candidateGroups(),
                request.filter().candidateUsers(),
                request.tenancyId());

        var entity = subscriptionService.register(
                request.peerId(), request.callbackUrl(), request.baseUrl(),
                request.tenancyId(), filter,
                request.capabilitiesJson(), hmacSecret);

        return new FederationSubscriptionResult(entity.id, entity.status.name());
    }

    @Override
    public void deregister(UUID subscriptionId, String tenancyId) {
        FederationSubscriptionEntity sub = FederationSubscriptionEntity.findById(subscriptionId);
        if (sub == null) {
            throw new IllegalArgumentException("Subscription not found: " + subscriptionId);
        }
        sub.status = FederationSubscriptionEntity.SubscriptionStatus.DEREGISTERED;
    }

    @Override
    public FederationSubscriptionResult reactivate(UUID subscriptionId, String tenancyId) {
        FederationSubscriptionEntity sub = FederationSubscriptionEntity.findById(subscriptionId);
        if (sub == null) {
            return null;
        }
        sub.status = FederationSubscriptionEntity.SubscriptionStatus.ACTIVE;
        sub.consecutiveFailures = 0;
        return new FederationSubscriptionResult(sub.id, sub.status.name());
    }
}
