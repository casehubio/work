package io.casehub.work.federation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.work.api.WorkItem;
import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.federation.subscription.FederationSubscriptionService;
import io.casehub.work.federation.transport.HmacSigner;
import io.casehub.work.memory.InMemoryWorkItemStore;
import io.casehub.platform.api.identity.CurrentPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


class FederationIntegrationTest {

    private final ObjectMapper                  objectMapper        = new ObjectMapper();
    private final InMemoryWorkItemStore         store               = new InMemoryWorkItemStore();
    private final FederationSubscriptionService subscriptionService = mock(FederationSubscriptionService.class);
    private final byte[]                        HMAC_SECRET         = "integration-test-secret-32bytes!".getBytes(StandardCharsets.UTF_8);

    private FederationGuardStore guardedStore;
    private FederationReceiver   receiver;

    @BeforeEach
    void setUp() throws Exception {
        CurrentPrincipal principal = mock(CurrentPrincipal.class);
        when(principal.tenancyId()).thenReturn("tenant-default");
        setField(store, "currentPrincipal", principal);

        guardedStore = new FederationGuardStore();
        setField(guardedStore, "delegate", store);

        receiver = new FederationReceiver();
        setField(receiver, "workItemStore", guardedStore);
        setField(receiver, "objectMapper", objectMapper);
        setField(receiver, "subscriptionService", subscriptionService);

        var tenantRunner = mock(io.casehub.work.runtime.service.TenantContextRunner.class);
        doAnswer(inv -> { ((Runnable) inv.getArgument(1)).run(); return null; })
                .when(tenantRunner).runInTenantContext(any(String.class), any(Runnable.class));
        setField(receiver, "tenantContextRunner", tenantRunner);
    }

    @Test
    void shadowCreatedFromReceivedCloudEvent() {
        String originServiceId  = "service-a";
        UUID   originWorkItemId = UUID.randomUUID();

        String cloudEvent = buildCloudEvent("io.casehub.work.federation.created",
                                            originServiceId, originWorkItemId, "tenant-default", 1L,
                                            WorkItemStatus.PENDING, "Review document");
        String signature = HmacSigner.sign(cloudEvent, HMAC_SECRET);

        receiver.onEvent(cloudEvent, signature, HMAC_SECRET);

        var shadow = store.findByOrigin(originServiceId, originWorkItemId);
        assertTrue(shadow.isPresent(), "Shadow should exist after receiving created event");
        assertEquals(originServiceId, shadow.get().originServiceId());
        assertEquals(originWorkItemId, shadow.get().originWorkItemId());
        assertEquals(1L, shadow.get().originVersion());
        assertEquals(WorkItemStatus.PENDING, shadow.get().status());
        assertEquals("Review document", shadow.get().title());
    }

    @Test
    void shadowUpdatedFromLifecycleEvent() {
        String originServiceId  = "service-a";
        UUID   originWorkItemId = UUID.randomUUID();

        String createEvent = buildCloudEvent("io.casehub.work.federation.created",
                                             originServiceId, originWorkItemId, "tenant-default", 1L,
                                             WorkItemStatus.PENDING, "Task to assign");
        receiver.onEvent(createEvent, HmacSigner.sign(createEvent, HMAC_SECRET), HMAC_SECRET);

        String assignEvent = buildCloudEvent("io.casehub.work.federation.assigned",
                                             originServiceId, originWorkItemId, "tenant-default", 2L,
                                             WorkItemStatus.ASSIGNED, "Task to assign");
        receiver.onEvent(assignEvent, HmacSigner.sign(assignEvent, HMAC_SECRET), HMAC_SECRET);

        var shadow = store.findByOrigin(originServiceId, originWorkItemId);
        assertTrue(shadow.isPresent());
        assertEquals(WorkItemStatus.ASSIGNED, shadow.get().status());
        assertEquals(2L, shadow.get().originVersion());
    }

    @Test
    void staleEventDiscarded() {
        String originServiceId  = "service-a";
        UUID   originWorkItemId = UUID.randomUUID();

        String createEvent = buildCloudEvent("io.casehub.work.federation.created",
                                             originServiceId, originWorkItemId, "tenant-default", 5L,
                                             WorkItemStatus.ASSIGNED, "Already advanced");
        receiver.onEvent(createEvent, HmacSigner.sign(createEvent, HMAC_SECRET), HMAC_SECRET);

        String staleEvent = buildCloudEvent("io.casehub.work.federation.created",
                                            originServiceId, originWorkItemId, "tenant-default", 3L,
                                            WorkItemStatus.PENDING, "Old state");
        receiver.onEvent(staleEvent, HmacSigner.sign(staleEvent, HMAC_SECRET), HMAC_SECRET);

        var shadow = store.findByOrigin(originServiceId, originWorkItemId);
        assertTrue(shadow.isPresent());
        assertEquals(WorkItemStatus.ASSIGNED, shadow.get().status());
        assertEquals(5L, shadow.get().originVersion());
    }

    @Test
    void guardPreventsDirectShadowMutation() {
        String originServiceId  = "service-b";
        UUID   originWorkItemId = UUID.randomUUID();

        String createEvent = buildCloudEvent("io.casehub.work.federation.created",
                                             originServiceId, originWorkItemId, "tenant-default", 1L,
                                             WorkItemStatus.PENDING, "Protected shadow");
        receiver.onEvent(createEvent, HmacSigner.sign(createEvent, HMAC_SECRET), HMAC_SECRET);

        var shadow  = store.findByOrigin(originServiceId, originWorkItemId).orElseThrow();
        var mutated = shadow.toBuilder().status(WorkItemStatus.COMPLETED).build();

        assertThrows(FederatedWorkItemMutationException.class, () -> guardedStore.put(mutated));
    }

    @Test
    void guardAllowsLocalWorkItemThroughGuardedStore() {
        var local = WorkItem.builder()
                            .id(UUID.randomUUID()).title("local task").createdBy("sys")
                            .status(WorkItemStatus.PENDING).priority(WorkItemPriority.MEDIUM)
                            .build();

        WorkItem saved = guardedStore.put(local);
        assertNotNull(saved);
        assertNull(saved.originServiceId());
    }

    private String buildCloudEvent(String type, String serviceId, UUID workItemId,
                                   String tenancyId, long version, WorkItemStatus status,
                                   String title) {
        return String.format(
                "{\"specversion\":\"1.0\",\"type\":\"%s\",\"source\":\"urn:casehub:work:%s\"," +
                "\"id\":\"%s\",\"tenancyid\":\"%s\",\"workitemversion\":%d," +
                "\"data\":{\"id\":\"%s\",\"title\":\"%s\",\"tenancyId\":\"%s\",\"status\":\"%s\"," +
                "\"priority\":\"MEDIUM\",\"createdBy\":\"system\",\"createdAt\":\"%s\"}}",
                type, serviceId, UUID.randomUUID(), tenancyId, version,
                workItemId, title, tenancyId, status.name(),
                java.time.Instant.now().toString());
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
