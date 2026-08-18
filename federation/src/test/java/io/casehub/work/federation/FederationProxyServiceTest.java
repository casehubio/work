package io.casehub.work.federation;

import io.casehub.work.api.WorkItem;
import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.api.spi.WorkItemOperations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FederationProxyServiceTest {

    private final WorkItemOperations delegate = mock(WorkItemOperations.class);
    private final FederationProxy proxy = mock(FederationProxy.class);
    private FederationProxyService proxyService;

    @BeforeEach
    void setUp() throws Exception {
        proxyService = new FederationProxyService();
        setField(proxyService, "delegate", delegate);
        setField(proxyService, "proxy", proxy);
    }

    @Test
    void claimOnShadowProxiesToOwner() {
        UUID id = UUID.randomUUID();
        WorkItem shadow = shadow(id);
        when(delegate.findById(id)).thenReturn(Optional.of(shadow));
        when(proxy.claim(shadow, "user1")).thenReturn(shadow);

        proxyService.claim(id, "user1");

        verify(proxy).claim(shadow, "user1");
        verify(delegate, never()).claim(any(), any());
    }

    @Test
    void claimOnLocalDelegatesToService() {
        UUID id = UUID.randomUUID();
        WorkItem local = local(id);
        when(delegate.findById(id)).thenReturn(Optional.of(local));
        when(delegate.claim(id, "user1")).thenReturn(local);

        proxyService.claim(id, "user1");

        verify(delegate).claim(id, "user1");
        verify(proxy, never()).claim(any(), any());
    }

    @Test
    void completeOnShadowProxiesToOwner() {
        UUID id = UUID.randomUUID();
        WorkItem shadow = shadow(id);
        when(delegate.findById(id)).thenReturn(Optional.of(shadow));
        when(proxy.complete(shadow, "actor", "done", "approved")).thenReturn(shadow);

        proxyService.complete(id, "actor", "done", "approved");

        verify(proxy).complete(shadow, "actor", "done", "approved");
    }

    @Test
    void rejectOnShadowProxiesToOwner() {
        UUID id = UUID.randomUUID();
        WorkItem shadow = shadow(id);
        when(delegate.findById(id)).thenReturn(Optional.of(shadow));
        when(proxy.reject(shadow, "actor", "nope", "denied")).thenReturn(shadow);

        proxyService.reject(id, "actor", "nope", "denied");

        verify(proxy).reject(shadow, "actor", "nope", "denied");
    }

    @Test
    void queryMethodsDelegateTransparently() {
        UUID id = UUID.randomUUID();
        proxyService.findById(id);
        verify(delegate).findById(id);
    }

    @Test
    void systemMethodsDelegateTransparently() {
        UUID id = UUID.randomUUID();
        proxyService.completeFromSystem(id, "sys", "auto-done");
        verify(delegate).completeFromSystem(id, "sys", "auto-done");
    }

    private WorkItem shadow(UUID id) {
        return WorkItem.builder()
                .id(id).title("shadow").createdBy("sys")
                .status(WorkItemStatus.PENDING).priority(WorkItemPriority.MEDIUM)
                .tenancyId("tenant-1")
                .originServiceId("remote-svc")
                .originWorkItemId(UUID.randomUUID())
                .build();
    }

    private WorkItem local(UUID id) {
        return WorkItem.builder()
                .id(id).title("local").createdBy("sys")
                .status(WorkItemStatus.PENDING).priority(WorkItemPriority.MEDIUM)
                .tenancyId("tenant-1")
                .build();
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
