package io.casehub.work.api;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorkItemFederationFieldsTest {

    @Test
    void shadowWorkItemHasFederationFields() {
        UUID originId = UUID.randomUUID();
        var shadow = WorkItem.builder()
                .id(UUID.randomUUID()).title("Remote task").createdBy("system")
                .status(WorkItemStatus.PENDING).priority(WorkItemPriority.MEDIUM)
                .originServiceId("service-a")
                .originWorkItemId(originId)
                .originVersion(5L)
                .build();
        assertEquals("service-a", shadow.originServiceId());
        assertEquals(originId, shadow.originWorkItemId());
        assertEquals(5L, shadow.originVersion());
    }

    @Test
    void localWorkItemHasNullFederationFields() {
        var local = WorkItem.builder()
                .id(UUID.randomUUID()).title("Local task").createdBy("system")
                .status(WorkItemStatus.PENDING).priority(WorkItemPriority.MEDIUM)
                .build();
        assertNull(local.originServiceId());
        assertNull(local.originWorkItemId());
        assertNull(local.originVersion());
    }

    @Test
    void toBuilderPreservesFederationFields() {
        UUID originId = UUID.randomUUID();
        var shadow = WorkItem.builder()
                .id(UUID.randomUUID()).title("Remote task").createdBy("system")
                .status(WorkItemStatus.PENDING).priority(WorkItemPriority.MEDIUM)
                .originServiceId("service-a")
                .originWorkItemId(originId)
                .originVersion(3L)
                .build();
        var copy = shadow.toBuilder().status(WorkItemStatus.ASSIGNED).build();
        assertEquals("service-a", copy.originServiceId());
        assertEquals(originId, copy.originWorkItemId());
        assertEquals(3L, copy.originVersion());
        assertEquals(WorkItemStatus.ASSIGNED, copy.status());
    }
}
