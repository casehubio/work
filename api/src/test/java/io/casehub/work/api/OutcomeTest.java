package io.casehub.work.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OutcomeTest {

    @Test
    void outcome_nameAndDisplayName() {
        final Outcome outcome = new Outcome("approved", "Approved", null);
        assertThat(outcome.name()).isEqualTo("approved");
        assertThat(outcome.displayName()).isEqualTo("Approved");
        assertThat(outcome.condition()).isNull();
    }

    @Test
    void outcome_nameOnly_nullDisplayName() {
        final Outcome outcome = new Outcome("rejected", null, null);
        assertThat(outcome.name()).isEqualTo("rejected");
        assertThat(outcome.displayName()).isNull();
        assertThat(outcome.condition()).isNull();
    }

    @Test
    void outcome_withCondition() {
        final Outcome outcome = new Outcome("escalate", "Escalate", "workItem.priority.name() == 'URGENT'");
        assertThat(outcome.condition()).isEqualTo("workItem.priority.name() == 'URGENT'");
    }
}
