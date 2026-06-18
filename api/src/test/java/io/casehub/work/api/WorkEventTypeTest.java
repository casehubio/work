package io.casehub.work.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class WorkEventTypeTest {

    @Test
    void allExpectedValuesExist() {
        assertThat(WorkEventType.values()).extracting(Enum::name)
                .containsExactlyInAnyOrder(
                        "CREATED", "ASSIGNED", "STARTED", "COMPLETED", "REJECTED", "FAULTED",
                        "DELEGATED", "DELEGATION_ACCEPTED", "DELEGATION_DECLINED", "RELEASED",
                        "SUSPENDED", "RESUMED", "CANCELLED", "OBSOLETE", "EXPIRED", "CLAIM_EXPIRED",
                        "SPAWNED", "ESCALATED", "DEADLINE_EXTENDED", "SLA_REASSIGNED", "SLA_EXTENDED",
                        "SIGNAL_RECEIVED", "PROGRESS_UPDATE", "LABEL_ADDED", "LABEL_REMOVED");
    }

    @Test
    void faulted_isAccessible() {
        assertThat(WorkEventType.valueOf("FAULTED")).isEqualTo(WorkEventType.FAULTED);
    }

    @Test
    void obsolete_isAccessible() {
        assertThat(WorkEventType.valueOf("OBSOLETE")).isEqualTo(WorkEventType.OBSOLETE);
    }

    @Test
    void delegationAccepted_isAccessible() {
        assertThat(WorkEventType.valueOf("DELEGATION_ACCEPTED")).isEqualTo(WorkEventType.DELEGATION_ACCEPTED);
    }

    @Test
    void delegationDeclined_isAccessible() {
        assertThat(WorkEventType.valueOf("DELEGATION_DECLINED")).isEqualTo(WorkEventType.DELEGATION_DECLINED);
    }

    @Test
    void slaReassigned_isAccessible() {
        assertThat(WorkEventType.valueOf("SLA_REASSIGNED")).isEqualTo(WorkEventType.SLA_REASSIGNED);
    }

    @Test
    void slaExtended_isAccessible() {
        assertThat(WorkEventType.valueOf("SLA_EXTENDED")).isEqualTo(WorkEventType.SLA_EXTENDED);
    }

    @Test
    void progressUpdate_isAccessible() {
        assertThat(WorkEventType.valueOf("PROGRESS_UPDATE")).isEqualTo(WorkEventType.PROGRESS_UPDATE);
    }

    @Test
    void labelAdded_isAccessible() {
        assertThat(WorkEventType.valueOf("LABEL_ADDED")).isEqualTo(WorkEventType.LABEL_ADDED);
    }

    @Test
    void labelRemoved_isAccessible() {
        assertThat(WorkEventType.valueOf("LABEL_REMOVED")).isEqualTo(WorkEventType.LABEL_REMOVED);
    }

    @Test
    void concreteEvent_implementsAbstractMethods() {
        var event = new WorkLifecycleEvent() {
            @Override
            public WorkEventType eventType() {
                return WorkEventType.CREATED;
            }

            @Override
            public Map<String, Object> context() {
                return Map.of("id", "x");
            }

            @Override
            public Object source() {
                return "test-source";
            }
        };
        assertThat(event.eventType()).isEqualTo(WorkEventType.CREATED);
        assertThat(event.context()).containsKey("id");
        assertThat(event.source()).isEqualTo("test-source");
    }
}
