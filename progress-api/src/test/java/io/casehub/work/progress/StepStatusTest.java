package io.casehub.work.progress;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StepStatusTest {

    @Test
    void onlySkippedIsTerminal() {
        assertThat(StepStatus.SKIPPED.isTerminal()).isTrue();
        assertThat(StepStatus.PENDING.isTerminal()).isFalse();
        assertThat(StepStatus.ACTIVE.isTerminal()).isFalse();
        assertThat(StepStatus.COMPLETED.isTerminal()).isFalse();
        assertThat(StepStatus.FAILED.isTerminal()).isFalse();
    }

    @Test
    void completedAndSkippedAreDone() {
        assertThat(StepStatus.COMPLETED.isDone()).isTrue();
        assertThat(StepStatus.SKIPPED.isDone()).isTrue();
        assertThat(StepStatus.PENDING.isDone()).isFalse();
        assertThat(StepStatus.ACTIVE.isDone()).isFalse();
        assertThat(StepStatus.FAILED.isDone()).isFalse();
    }

    @Test
    void completedSkippedAndFailedAreQuiescent() {
        assertThat(StepStatus.COMPLETED.isQuiescent()).isTrue();
        assertThat(StepStatus.SKIPPED.isQuiescent()).isTrue();
        assertThat(StepStatus.FAILED.isQuiescent()).isTrue();
        assertThat(StepStatus.PENDING.isQuiescent()).isFalse();
        assertThat(StepStatus.ACTIVE.isQuiescent()).isFalse();
    }

    @Test
    void onlyActiveIsActive() {
        assertThat(StepStatus.ACTIVE.isActive()).isTrue();
        assertThat(StepStatus.PENDING.isActive()).isFalse();
        assertThat(StepStatus.COMPLETED.isActive()).isFalse();
        assertThat(StepStatus.SKIPPED.isActive()).isFalse();
        assertThat(StepStatus.FAILED.isActive()).isFalse();
    }
}
