package io.casehub.work.federation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.work.api.WorkItem;
import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.federation.subscription.FederationSubscriptionEntity;
import io.casehub.work.federation.subscription.FederationSubscriptionService;
import io.casehub.work.federation.transport.FederationTransport;
import io.casehub.work.api.WorkItemLifecycleEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class FederationEventRouterTest {

    private final FederationSubscriptionService subscriptionService = mock(FederationSubscriptionService.class);
    private final FederationTransport transport = mock(FederationTransport.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private FederationEventRouter router;

    @BeforeEach
    void setUp() throws Exception {
        router = new FederationEventRouter();
        setField(router, "subscriptionService", subscriptionService);
        setField(router, "transport", transport);
        setField(router, "objectMapper", objectMapper);
        setField(router, "config", new TestFederationConfig());
    }

    @Test
    void skipsShadowWorkItems() {
        WorkItem shadow = WorkItem.builder()
                .id(UUID.randomUUID()).title("shadow").createdBy("sys")
                .status(WorkItemStatus.PENDING).priority(WorkItemPriority.MEDIUM)
                .tenancyId("t1")
                .originServiceId("remote-svc")
                .originWorkItemId(UUID.randomUUID())
                .build();
        var event = WorkItemLifecycleEvent.of("CREATED", shadow, "sys", null);

        router.onWorkItemLifecycle(event);

        verifyNoInteractions(subscriptionService);
        verifyNoInteractions(transport);
    }

    @Test
    void skipsNullWorkItem() {
        var event = WorkItemLifecycleEvent.fromWire(
                "io.casehub.work.workitem.created", "/workitems/x", "x",
                UUID.randomUUID(), WorkItemStatus.PENDING, java.time.Instant.now(),
                "sys", null, null, null, null, "t1", null, null, null, null, List.of());
        router.onWorkItemLifecycle(event);
        verifyNoInteractions(transport);
    }

    @Test
    void matchesSubscriptionsOnCreation() {
        WorkItem local = localItem();
        var event = WorkItemLifecycleEvent.of("CREATED", local, "sys", null);

        var sub = mockSubscription();
        when(subscriptionService.matchSubscriptions(local)).thenReturn(List.of(sub));

        router.onWorkItemLifecycle(event);

        verify(subscriptionService).lockOn(sub.id, local.id());
        verify(transport).send(any(), eq(sub.callbackUrl), eq(sub.hmacSecretEncrypted));
        verify(subscriptionService).recordSuccess(sub.id);
    }

    @Test
    void deliversToLockedSubscriptionsOnSubsequentEvents() {
        WorkItem local = localItem();
        var event = WorkItemLifecycleEvent.of("ASSIGNED", local, "actor1", null);

        var sub = mockSubscription();
        when(subscriptionService.findLockedSubscriptions(local.id())).thenReturn(List.of(sub));

        router.onWorkItemLifecycle(event);

        verify(subscriptionService, never()).matchSubscriptions(any());
        verify(transport).send(any(), eq(sub.callbackUrl), eq(sub.hmacSecretEncrypted));
    }

    @Test
    void noDeliveryWhenNoSubscribers() {
        WorkItem local = localItem();
        var event = WorkItemLifecycleEvent.of("CREATED", local, "sys", null);

        when(subscriptionService.matchSubscriptions(local)).thenReturn(List.of());

        router.onWorkItemLifecycle(event);

        verifyNoInteractions(transport);
    }

    @Test
    void recordsFailureOnTransportError() {
        WorkItem local = localItem();
        var event = WorkItemLifecycleEvent.of("CREATED", local, "sys", null);

        var sub = mockSubscription();
        when(subscriptionService.matchSubscriptions(local)).thenReturn(List.of(sub));
        doThrow(new RuntimeException("network error")).when(transport)
                .send(any(), eq(sub.callbackUrl), eq(sub.hmacSecretEncrypted));

        router.onWorkItemLifecycle(event);

        verify(subscriptionService).recordFailure(sub.id);
        verify(subscriptionService, never()).recordSuccess(sub.id);
    }

    private WorkItem localItem() {
        return WorkItem.builder()
                .id(UUID.randomUUID()).title("local task").createdBy("sys")
                .status(WorkItemStatus.PENDING).priority(WorkItemPriority.MEDIUM)
                .tenancyId("tenant-1").candidateGroups("legal").version(1L)
                .build();
    }

    private FederationSubscriptionEntity mockSubscription() {
        var sub = new FederationSubscriptionEntity();
        sub.id = UUID.randomUUID();
        sub.callbackUrl = "https://service-b.example.com/federation/events";
        sub.hmacSecretEncrypted = "test-secret".getBytes(StandardCharsets.UTF_8);
        sub.status = FederationSubscriptionEntity.SubscriptionStatus.ACTIVE;
        return sub;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    static class TestFederationConfig implements FederationConfig {
        @Override public String serviceId() { return "test-service"; }
        @Override public int proxyTimeoutSeconds() { return 5; }
    }
}
