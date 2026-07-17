package io.casehub.work.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class WorkItemEventTest {

    private WorkItemEvent event(final WorkItemRef ref) {
        return new WorkItemEvent() {
            @Override public WorkItemRef ref() { return ref; }
            @Override public WorkEventType eventType() { return WorkEventType.CREATED; }
            @Override public Instant occurredAt() { return Instant.now(); }
            @Override public String actor() { return "test"; }
            @Override public String detail() { return null; }
        };
    }

    @Test
    void defaultMethods_delegateToRef() {
        final UUID id = UUID.randomUUID();
        final WorkItemRef ref = new WorkItemRef(id, WorkItemStatus.IN_PROGRESS, "caller-1",
                "bob", "{\"x\":1}", "team-b", "rejected", "tenant-2", "{\"data\":true}",
                null, null);

        final WorkItemEvent event = event(ref);

        assertThat(event.workItemId()).isEqualTo(id);
        assertThat(event.status()).isEqualTo(WorkItemStatus.IN_PROGRESS);
        assertThat(event.callerRef()).isEqualTo("caller-1");
        assertThat(event.assigneeId()).isEqualTo("bob");
        assertThat(event.resolution()).isEqualTo("{\"x\":1}");
        assertThat(event.candidateGroups()).isEqualTo("team-b");
        assertThat(event.outcome()).isEqualTo("rejected");
        assertThat(event.tenancyId()).isEqualTo("tenant-2");
    }

    @Test
    void defaultMethods_handleNullRefFields() {
        final WorkItemRef ref = new WorkItemRef(UUID.randomUUID(), WorkItemStatus.PENDING,
                null, null, null, null, null, null, null, null, null);

        final WorkItemEvent event = event(ref);

        assertThat(event.callerRef()).isNull();
        assertThat(event.assigneeId()).isNull();
        assertThat(event.resolution()).isNull();
    }
}
