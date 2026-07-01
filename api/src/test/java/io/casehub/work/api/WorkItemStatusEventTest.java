package io.casehub.work.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class WorkItemStatusEventTest {

    @Test
    void record_carriesAllFields() {
        final UUID id = UUID.randomUUID();
        final Instant now = Instant.now();
        final WorkItemStatusEvent event = new WorkItemStatusEvent(
                WorkEventType.COMPLETED, id, WorkItemStatus.COMPLETED,
                "alice", "done", "caller-1", "alice", "team-a",
                "approved", "tenant-1", now);

        assertThat(event.eventType()).isEqualTo(WorkEventType.COMPLETED);
        assertThat(event.workItemId()).isEqualTo(id);
        assertThat(event.status()).isEqualTo(WorkItemStatus.COMPLETED);
        assertThat(event.actor()).isEqualTo("alice");
        assertThat(event.detail()).isEqualTo("done");
        assertThat(event.callerRef()).isEqualTo("caller-1");
        assertThat(event.assigneeId()).isEqualTo("alice");
        assertThat(event.candidateGroups()).isEqualTo("team-a");
        assertThat(event.outcome()).isEqualTo("approved");
        assertThat(event.tenancyId()).isEqualTo("tenant-1");
        assertThat(event.occurredAt()).isEqualTo(now);
    }

    @Test
    void record_handlesNullFields() {
        final WorkItemStatusEvent event = new WorkItemStatusEvent(
                WorkEventType.CREATED, UUID.randomUUID(), WorkItemStatus.PENDING,
                "system", null, null, null, null, null, null, Instant.now());

        assertThat(event.detail()).isNull();
        assertThat(event.callerRef()).isNull();
        assertThat(event.outcome()).isNull();
    }
}
