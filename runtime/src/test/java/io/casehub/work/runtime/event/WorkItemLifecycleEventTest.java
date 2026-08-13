package io.casehub.work.runtime.event;

import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.api.WorkItem;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure JUnit 5 unit tests for {@link WorkItemLifecycleEvent} — no container needed.
 *
 * <p>
 * Refs #25, #22, #118
 */
class WorkItemLifecycleEventTest {

    private WorkItem workItem(final UUID id, final WorkItemStatus status) {
        return WorkItem.builder()
                .id(id)
                .status(status)
                .build();
    }

    @Test
    void of_buildsCorrectTypeString() {
        UUID id = UUID.randomUUID();
        WorkItemLifecycleEvent e = WorkItemLifecycleEvent.of("CREATED",
                workItem(id, WorkItemStatus.PENDING), "system", null);
        assertThat(e.type()).isEqualTo("io.casehub.work.workitem.created");
    }

    @Test
    void of_buildsCorrectSourceUri() {
        UUID id = UUID.randomUUID();
        WorkItemLifecycleEvent e = WorkItemLifecycleEvent.of("ASSIGNED",
                workItem(id, WorkItemStatus.ASSIGNED), "alice", null);
        assertThat(e.sourceUri()).isEqualTo("/workitems/" + id);
    }

    @Test
    void of_workItemReturnsDomain() {
        UUID                   id = UUID.randomUUID();
        WorkItem               wi = workItem(id, WorkItemStatus.ASSIGNED);
        WorkItemLifecycleEvent e  = WorkItemLifecycleEvent.of("ASSIGNED", wi, "alice", null);
        assertThat(e.workItem()).isSameAs(wi);
    }

    @Test
    void of_subjectIsWorkItemId() {
        UUID id = UUID.randomUUID();
        WorkItemLifecycleEvent e = WorkItemLifecycleEvent.of("COMPLETED",
                workItem(id, WorkItemStatus.COMPLETED), "alice", null);
        assertThat(e.subject()).isEqualTo(id.toString());
    }

    @Test
    void of_setsWorkItemIdAndStatus() {
        UUID id = UUID.randomUUID();
        WorkItemLifecycleEvent e = WorkItemLifecycleEvent.of("REJECTED",
                workItem(id, WorkItemStatus.REJECTED), "bob", "reason");
        assertThat(e.workItemId()).isEqualTo(id);
        assertThat(e.status()).isEqualTo(WorkItemStatus.REJECTED);
        assertThat(e.actor()).isEqualTo("bob");
        assertThat(e.detail()).isEqualTo("reason");
    }

    @Test
    void of_occurredAtIsNotNull() {
        WorkItemLifecycleEvent e = WorkItemLifecycleEvent.of("DELEGATED",
                workItem(UUID.randomUUID(), WorkItemStatus.PENDING), "alice", "to:bob");
        assertThat(e.occurredAt()).isNotNull();
    }

    @Test
    void of_typeIsAlwaysLowercase() {
        // "EXPIRED" -> "io.casehub.work.workitem.expired"
        WorkItemLifecycleEvent e = WorkItemLifecycleEvent.of("EXPIRED",
                workItem(UUID.randomUUID(), WorkItemStatus.EXPIRED), "system", null);
        assertThat(e.type()).doesNotContain("EXPIRED");
        assertThat(e.type()).endsWith("expired");
    }

    @Test
    void of_nullDetailAllowed() {
        WorkItemLifecycleEvent e = WorkItemLifecycleEvent.of("CREATED",
                workItem(UUID.randomUUID(), WorkItemStatus.PENDING), "system", null);
        assertThat(e.detail()).isNull();
    }

    @Test
    void ref_returnsWorkItemRefFromDomain() {
        final WorkItem workItem = WorkItem.builder()
                .id(UUID.randomUUID())
                .status(WorkItemStatus.IN_PROGRESS)
                .callerRef("case:1/pi:2")
                .assigneeId("alice")
                .resolution("{}")
                .candidateGroups("team-a")
                .outcome("approved")
                .tenancyId("tenant-1")
                .build();

        final WorkItemLifecycleEvent event = WorkItemLifecycleEvent.of("COMPLETED", workItem, "alice", null);
        final io.casehub.work.api.WorkItemRef ref = event.ref();

        assertThat(ref.id()).isEqualTo(workItem.id());
        assertThat(ref.callerRef()).isEqualTo("case:1/pi:2");
        assertThat(ref.assigneeId()).isEqualTo("alice");
        assertThat(ref.resolution()).isEqualTo("{}");
        assertThat(ref.candidateGroups()).isEqualTo("team-a");
        assertThat(ref.tenancyId()).isEqualTo("tenant-1");
    }

    @Test
    void ref_fromWireEvent_returnsWorkItemRefFromStoredFields() {
        final UUID id = UUID.randomUUID();
        final WorkItemLifecycleEvent wireEvent = WorkItemLifecycleEvent.fromWire(
                "io.casehub.work.workitem.completed", "/workitems/" + id, id.toString(),
                id, WorkItemStatus.COMPLETED, java.time.Instant.now(), "alice", null, null, null,
                "approved", "tenant-1",
                "case:1/pi:2", "alice", "{}", "team-a", java.util.List.of("approval"));

        final io.casehub.work.api.WorkItemRef ref = wireEvent.ref();

        assertThat(ref.id()).isEqualTo(id);
        assertThat(ref.callerRef()).isEqualTo("case:1/pi:2");
        assertThat(ref.assigneeId()).isEqualTo("alice");
        assertThat(ref.resolution()).isEqualTo("{}");
        assertThat(ref.candidateGroups()).isEqualTo("team-a");
    }


    @Test
    void implementsSubscribableEvent() {
        WorkItem wi = WorkItem.builder()
                .id(UUID.randomUUID())
                .status(WorkItemStatus.IN_PROGRESS)
                .tenancyId("test-tenant")
                .build();
        WorkItemLifecycleEvent e = WorkItemLifecycleEvent.of("CREATED", wi, "alice", null);

        assertThat(e).isInstanceOf(io.casehub.platform.api.subscription.SubscribableEvent.class);
        io.casehub.platform.api.subscription.SubscribableEvent se = e;
        assertThat(se.type()).isEqualTo("io.casehub.work.workitem.created");
        assertThat(se.tenancyId()).isEqualTo("test-tenant");
    }

}
