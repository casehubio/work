package io.casehub.work.federation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.work.api.WorkItem;
import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.api.spi.WorkItemStore;
import io.casehub.work.federation.subscription.FederationSubscriptionService;
import io.casehub.work.federation.transport.HmacSigner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class FederationReceiverTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WorkItemStore store = mock(WorkItemStore.class);
    private final FederationSubscriptionService subscriptionService = mock(FederationSubscriptionService.class);
    private final byte[] secret = "test-secret-32bytes-long!!!!!!!".getBytes(StandardCharsets.UTF_8);

    private FederationReceiver receiver;

    @BeforeEach
    void setUp() throws Exception {
        receiver = new FederationReceiver();
        setField(receiver, "workItemStore", store);
        setField(receiver, "objectMapper", objectMapper);
        setField(receiver, "subscriptionService", subscriptionService);

        var tenantRunner = mock(io.casehub.work.runtime.service.TenantContextRunner.class);
        doAnswer(inv -> { ((Runnable) inv.getArgument(1)).run(); return null; })
                .when(tenantRunner).runInTenantContext(any(String.class), any(Runnable.class));
        setField(receiver, "tenantContextRunner", tenantRunner);
    }

    @Test
    void createsNewShadowFromCreatedEvent() {
        String json = buildCloudEvent("io.casehub.work.federation.created",
                "svc-a", UUID.randomUUID(), "tenant-1", 1L, WorkItemStatus.PENDING);
        String sig = HmacSigner.sign(json, secret);

        when(store.findByOrigin(eq("svc-a"), any(UUID.class))).thenReturn(Optional.empty());
        when(store.put(any())).thenAnswer(inv -> inv.getArgument(0));

        receiver.onEvent(json, sig, secret);

        verify(store).put(argThat(wi -> {
            assertEquals("svc-a", wi.originServiceId());
            assertNotNull(wi.originWorkItemId());
            assertEquals(1L, wi.originVersion());
            assertEquals(WorkItemStatus.PENDING, wi.status());
            assertTrue(wi.callerRef() == null || wi.callerRef().startsWith("federation:svc-a:"));
            return true;
        }));
    }

    @Test
    void updatesShadowFromLifecycleEvent() {
        UUID originId = UUID.randomUUID();
        UUID shadowId = UUID.randomUUID();
        WorkItem existingShadow = WorkItem.builder()
                .id(shadowId).title("old").createdBy("sys")
                .status(WorkItemStatus.PENDING).priority(WorkItemPriority.MEDIUM)
                .originServiceId("svc-a").originWorkItemId(originId).originVersion(1L)
                .tenancyId("tenant-1")
                .build();

        String json = buildCloudEvent("io.casehub.work.federation.assigned",
                "svc-a", originId, "tenant-1", 2L, WorkItemStatus.ASSIGNED);
        String sig = HmacSigner.sign(json, secret);

        when(store.findByOrigin("svc-a", originId)).thenReturn(Optional.of(existingShadow));
        when(store.put(any())).thenAnswer(inv -> inv.getArgument(0));

        receiver.onEvent(json, sig, secret);

        verify(store).put(argThat(wi -> {
            assertEquals(shadowId, wi.id());
            assertEquals(WorkItemStatus.ASSIGNED, wi.status());
            assertEquals(2L, wi.originVersion());
            return true;
        }));
    }

    @Test
    void discardsStaleEvent() {
        UUID originId = UUID.randomUUID();
        WorkItem existingShadow = WorkItem.builder()
                .id(UUID.randomUUID()).title("current").createdBy("sys")
                .status(WorkItemStatus.ASSIGNED).priority(WorkItemPriority.MEDIUM)
                .originServiceId("svc-a").originWorkItemId(originId).originVersion(5L)
                .tenancyId("tenant-1")
                .build();

        String json = buildCloudEvent("io.casehub.work.federation.claimed",
                "svc-a", originId, "tenant-1", 3L, WorkItemStatus.ASSIGNED);
        String sig = HmacSigner.sign(json, secret);

        when(store.findByOrigin("svc-a", originId)).thenReturn(Optional.of(existingShadow));

        receiver.onEvent(json, sig, secret);

        verify(store, never()).put(any());
    }

    @Test
    void rejectsInvalidHmac() {
        String json = buildCloudEvent("io.casehub.work.federation.created",
                "svc-a", UUID.randomUUID(), "tenant-1", 1L, WorkItemStatus.PENDING);

        assertThrows(IllegalArgumentException.class,
                () -> receiver.onEvent(json, "bad-signature", secret));

        verify(store, never()).put(any());
    }

    @Test
    void removesTrackingOnTerminalEvent() {
        UUID originId = UUID.randomUUID();
        WorkItem existingShadow = WorkItem.builder()
                .id(UUID.randomUUID()).title("done").createdBy("sys")
                .status(WorkItemStatus.ASSIGNED).priority(WorkItemPriority.MEDIUM)
                .originServiceId("svc-a").originWorkItemId(originId).originVersion(4L)
                .tenancyId("tenant-1")
                .build();

        String json = buildCloudEvent("io.casehub.work.federation.completed",
                "svc-a", originId, "tenant-1", 5L, WorkItemStatus.COMPLETED);
        String sig = HmacSigner.sign(json, secret);

        when(store.findByOrigin("svc-a", originId)).thenReturn(Optional.of(existingShadow));
        when(store.put(any())).thenAnswer(inv -> inv.getArgument(0));

        receiver.onEvent(json, sig, secret);

        verify(subscriptionService).removeTracking(originId);
    }

    private String buildCloudEvent(String type, String serviceId, UUID workItemId,
                                    String tenancyId, long version, WorkItemStatus status) {
        ObjectNode ce = objectMapper.createObjectNode();
        ce.put("specversion", "1.0");
        ce.put("type", type);
        ce.put("source", "urn:casehub:work:" + serviceId);
        ce.put("id", UUID.randomUUID().toString());
        ce.put("tenancyid", tenancyId);
        ce.put("workitemversion", version);

        ObjectNode data = ce.putObject("data");
        data.put("id", workItemId.toString());
        data.put("title", "Test WorkItem");
        data.put("tenancyId", tenancyId);
        data.put("status", status.name());
        data.put("priority", "MEDIUM");
        data.put("createdBy", "system");
        data.put("createdAt", Instant.now().toString());

        return ce.toString();
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
