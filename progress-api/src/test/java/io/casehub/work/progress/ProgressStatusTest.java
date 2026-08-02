package io.casehub.work.progress;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProgressStatusTest {

    @Test
    void noStatusIsTerminal() {
        for (ProgressStatus status : ProgressStatus.values()) {
            assertThat(status.isTerminal()).as("ProgressStatus.%s.isTerminal()", status).isFalse();
        }
    }

    @Test
    void completedAndFailedAreQuiescent() {
        assertThat(ProgressStatus.COMPLETED.isQuiescent()).isTrue();
        assertThat(ProgressStatus.FAILED.isQuiescent()).isTrue();
    }

    @Test
    void pendingAndActiveAreNotQuiescent() {
        assertThat(ProgressStatus.PENDING.isQuiescent()).isFalse();
        assertThat(ProgressStatus.ACTIVE.isQuiescent()).isFalse();
    }

    @Test
    void onlyActiveIsActive() {
        assertThat(ProgressStatus.ACTIVE.isActive()).isTrue();
        assertThat(ProgressStatus.PENDING.isActive()).isFalse();
        assertThat(ProgressStatus.COMPLETED.isActive()).isFalse();
        assertThat(ProgressStatus.FAILED.isActive()).isFalse();
    }
}
