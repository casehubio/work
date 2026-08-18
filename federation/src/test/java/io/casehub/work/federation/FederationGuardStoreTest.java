package io.casehub.work.federation;

import io.casehub.work.api.WorkItem;
import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.api.spi.WorkItemStore;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FederationGuardStoreTest {

    private final WorkItemStore delegate = mock(WorkItemStore.class);

    private final FederationGuardStore guard = createGuard();

    private FederationGuardStore createGuard() {
        var g = new FederationGuardStore();
        try {
            var field = FederationGuardStore.class.getDeclaredField("delegate");
            field.setAccessible(true);
            field.set(g, delegate);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return g;
    }

    @Test
    void rejectsShadowMutationWithoutSyncContext() {
        var shadow = WorkItem.builder()
                .id(UUID.randomUUID()).title("shadow").createdBy("sys")
                .status(WorkItemStatus.ASSIGNED).priority(WorkItemPriority.MEDIUM)
                .originServiceId("remote-svc")
                .originWorkItemId(UUID.randomUUID())
                .build();

        assertThrows(FederatedWorkItemMutationException.class,
                () -> guard.put(shadow));
        verify(delegate, never()).put(any());
    }

    @Test
    void allowsShadowMutationWithSyncContext() {
        var shadow = WorkItem.builder()
                .id(UUID.randomUUID()).title("shadow").createdBy("sys")
                .status(WorkItemStatus.ASSIGNED).priority(WorkItemPriority.MEDIUM)
                .originServiceId("remote-svc")
                .originWorkItemId(UUID.randomUUID())
                .build();
        when(delegate.put(shadow)).thenReturn(shadow);

        try (var ctx = FederationSyncContext.activate()) {
            WorkItem result = guard.put(shadow);
            assertNotNull(result);
            verify(delegate).put(shadow);
        }
    }

    @Test
    void allowsLocalWorkItemMutation() {
        var local = WorkItem.builder()
                .id(UUID.randomUUID()).title("local").createdBy("sys")
                .status(WorkItemStatus.PENDING).priority(WorkItemPriority.MEDIUM)
                .build();
        when(delegate.put(local)).thenReturn(local);

        guard.put(local);
        verify(delegate).put(local);
    }

    @Test
    void syncContextCleansUpOnException() {
        var shadow = WorkItem.builder()
                .id(UUID.randomUUID()).title("shadow").createdBy("sys")
                .status(WorkItemStatus.ASSIGNED).priority(WorkItemPriority.MEDIUM)
                .originServiceId("remote-svc")
                .originWorkItemId(UUID.randomUUID())
                .build();
        when(delegate.put(shadow)).thenThrow(new RuntimeException("store failure"));

        try (var ctx = FederationSyncContext.activate()) {
            assertThrows(RuntimeException.class, () -> guard.put(shadow));
        }
        // After close, context should be inactive
        assertFalse(FederationSyncContext.isActive());
        // Shadow mutation without context should now be blocked
        assertThrows(FederatedWorkItemMutationException.class,
                () -> guard.put(shadow));
    }

    @Test
    void queryMethodsDelegateTransparently() {
        UUID id = UUID.randomUUID();
        guard.get(id);
        verify(delegate).get(id);
    }
}
