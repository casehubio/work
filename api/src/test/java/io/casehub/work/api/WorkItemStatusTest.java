package io.casehub.work.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class WorkItemStatusTest {

    @Test
    void expired_isTerminal() {
        assertThat(io.casehub.work.api.WorkItemStatus.EXPIRED.isTerminal()).isTrue();
    }

    @Test
    void delegated_isNotTerminal() {
        assertThat(io.casehub.work.api.WorkItemStatus.DELEGATED.isTerminal()).isFalse();
    }

    @Test
    void completed_isTerminal() {
        assertThat(io.casehub.work.api.WorkItemStatus.COMPLETED.isTerminal()).isTrue();
    }

    @Test
    void rejected_isTerminal() {
        assertThat(io.casehub.work.api.WorkItemStatus.REJECTED.isTerminal()).isTrue();
    }

    @Test
    void cancelled_isTerminal() {
        assertThat(io.casehub.work.api.WorkItemStatus.CANCELLED.isTerminal()).isTrue();
    }

    @Test
    void escalated_isTerminal() {
        assertThat(io.casehub.work.api.WorkItemStatus.ESCALATED.isTerminal()).isTrue();
    }

    @Test
    void pending_isActive() {
        assertThat(io.casehub.work.api.WorkItemStatus.PENDING.isActive()).isTrue();
    }

    @Test
    void assigned_isActive() {
        assertThat(io.casehub.work.api.WorkItemStatus.ASSIGNED.isActive()).isTrue();
    }

    @Test
    void inProgress_isActive() {
        assertThat(io.casehub.work.api.WorkItemStatus.IN_PROGRESS.isActive()).isTrue();
    }

    @Test
    void suspended_isActive() {
        assertThat(io.casehub.work.api.WorkItemStatus.SUSPENDED.isActive()).isTrue();
    }

    @Test
    void expired_isNotActive() {
        assertThat(io.casehub.work.api.WorkItemStatus.EXPIRED.isActive()).isFalse();
    }

    @Test
    void completed_isNotActive() {
        assertThat(io.casehub.work.api.WorkItemStatus.COMPLETED.isActive()).isFalse();
    }

    @Test
    void delegated_isActive() {
        assertThat(io.casehub.work.api.WorkItemStatus.DELEGATED.isActive()).isTrue();
    }

    @Test
    void faulted_isTerminal() {
        assertThat(io.casehub.work.api.WorkItemStatus.FAULTED.isTerminal()).isTrue();
    }

    @Test
    void faulted_isNotActive() {
        assertThat(io.casehub.work.api.WorkItemStatus.FAULTED.isActive()).isFalse();
    }

    @Test
    void obsolete_isTerminal() {
        assertThat(io.casehub.work.api.WorkItemStatus.OBSOLETE.isTerminal()).isTrue();
    }

    @Test
    void obsolete_isNotActive() {
        assertThat(io.casehub.work.api.WorkItemStatus.OBSOLETE.isActive()).isFalse();
    }

    @Test
    void terminalStatusesConstant_matchesIsTerminal() {
        var expected = Arrays.stream(io.casehub.work.api.WorkItemStatus.values())
                .filter(io.casehub.work.api.WorkItemStatus::isTerminal)
                .toList();
        assertThat(io.casehub.work.api.WorkItemStatus.TERMINAL_STATUSES)
                .as("TERMINAL_STATUSES constant must match isTerminal() for all enum values")
                .containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void terminalStatusesAreNeverActive() {
        for (final io.casehub.work.api.WorkItemStatus s : io.casehub.work.api.WorkItemStatus.values()) {
            if (s.isTerminal()) {
                assertThat(s.isActive())
                        .as("%s is both terminal and active — must be one or the other", s)
                        .isFalse();
            }
        }
    }
}
