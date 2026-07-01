package io.casehub.work.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class WorkItemRefTest {

    @Test
    void record_carriesAllFields() {
        final UUID id = UUID.randomUUID();
        final WorkItemRef ref = new WorkItemRef(id, WorkItemStatus.PENDING, "caller-1",
                "alice", "{}", "team-a", "approved", "tenant-1", "{\"key\":\"val\"}");

        assertThat(ref.id()).isEqualTo(id);
        assertThat(ref.status()).isEqualTo(WorkItemStatus.PENDING);
        assertThat(ref.callerRef()).isEqualTo("caller-1");
        assertThat(ref.assigneeId()).isEqualTo("alice");
        assertThat(ref.resolution()).isEqualTo("{}");
        assertThat(ref.candidateGroups()).isEqualTo("team-a");
        assertThat(ref.outcome()).isEqualTo("approved");
        assertThat(ref.tenancyId()).isEqualTo("tenant-1");
        assertThat(ref.payload()).isEqualTo("{\"key\":\"val\"}");
    }

    @Test
    void statusHelpers_delegateCorrectly() {
        final WorkItemRef active = new WorkItemRef(UUID.randomUUID(), WorkItemStatus.PENDING,
                null, null, null, null, null, null, null);
        final WorkItemRef terminal = new WorkItemRef(UUID.randomUUID(), WorkItemStatus.COMPLETED,
                null, null, null, null, null, null, null);

        assertThat(active.status().isActive()).isTrue();
        assertThat(active.status().isTerminal()).isFalse();
        assertThat(terminal.status().isTerminal()).isTrue();
        assertThat(terminal.status().isActive()).isFalse();
    }
}
