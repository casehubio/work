package io.casehub.work.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class CrossSystemRefTest {

    @Test
    void interfaceContract_systemAndEncode() {
        CrossSystemRef ref = new CrossSystemRef() {
            @Override public String system() { return "test"; }
            @Override public String encode() { return "test:hello"; }
        };
        assertThat(ref.system()).isEqualTo("test");
        assertThat(ref.encode()).isEqualTo("test:hello");
    }

    @Test
    void ledgerEntryId_nullAfterConstruction() {
        var event = wireEvent(null);
        assertThat(event.ledgerEntryId()).isNull();
    }

    @Test
    void ledgerEntryId_setViaSetter_returnsValue() {
        var event = wireEvent(null);
        UUID id = UUID.randomUUID();
        WorkItemLifecycleEvent.ledgerEntryIdSetter().set(event, id);
        assertThat(event.ledgerEntryId()).isEqualTo(id);
    }

    @Test
    void fromWire_withLedgerEntryId_preservesValue() {
        UUID ledgerId = UUID.randomUUID();
        UUID workItemId = UUID.randomUUID();
        var event = WorkItemLifecycleEvent.fromWire(
                "io.casehub.work.workitem.created", "/workitems/" + workItemId,
                workItemId.toString(), workItemId, WorkItemStatus.PENDING,
                java.time.Instant.now(), "sys", null, null, null, null, "t1",
                null, null, null, null, java.util.List.of(), ledgerId);
        assertThat(event.ledgerEntryId()).isEqualTo(ledgerId);
    }

    @Test
    void fromWire_withNullLedgerEntryId_returnsNull() {
        UUID workItemId = UUID.randomUUID();
        var event = WorkItemLifecycleEvent.fromWire(
                "io.casehub.work.workitem.created", "/workitems/" + workItemId,
                workItemId.toString(), workItemId, WorkItemStatus.PENDING,
                java.time.Instant.now(), "sys", null, null, null, null, "t1",
                null, null, null, null, java.util.List.of(), null);
        assertThat(event.ledgerEntryId()).isNull();
    }

    @Test
    void workItemEvent_defaultLedgerEntryId_isNull() {
        WorkItemEvent wie = new WorkItemEvent() {
            @Override public WorkItemRef ref() { return null; }
            @Override public WorkEventType eventType() { return null; }
            @Override public java.time.Instant occurredAt() { return null; }
            @Override public String actor() { return null; }
            @Override public String detail() { return null; }
        };
        assertThat(wie.ledgerEntryId()).isNull();
    }

    private static WorkItemLifecycleEvent wireEvent(UUID ledgerEntryId) {
        UUID workItemId = UUID.randomUUID();
        return WorkItemLifecycleEvent.fromWire(
                "io.casehub.work.workitem.created", "/workitems/" + workItemId,
                workItemId.toString(), workItemId, WorkItemStatus.PENDING,
                java.time.Instant.now(), "sys", null, null, null, null, "t1",
                null, null, null, null, java.util.List.of(), ledgerEntryId);
    }
}
