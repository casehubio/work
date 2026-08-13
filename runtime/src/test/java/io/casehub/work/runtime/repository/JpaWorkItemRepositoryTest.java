package io.casehub.work.runtime.repository;

import io.casehub.work.api.LabelPersistence;
import io.casehub.work.api.WorkItem;
import io.casehub.work.api.WorkItemLabel;
import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.api.WorkItemQuery;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.api.spi.WorkItemStore;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link WorkItemStore} JPA queries against real H2.
 *
 * <p>
 * Tests the store-layer queries directly, bypassing the service layer so that
 * timestamps can be set to past values that would not be reachable via normal lifecycle
 * operations.
 *
 * <p>
 * {@link io.casehub.work.runtime.service.ExpiryLifecycleService} depends on
 * {@link WorkItemStore#scan} with {@link WorkItemQuery#expired} and
 * {@link WorkItemQuery#claimExpired} — correctness here is critical.
 */
@QuarkusTest
@TestTransaction
class JpaWorkItemRepositoryTest {

    @Inject
    WorkItemStore workItemStore;

    @Inject
    AuditEntryStore auditStore;

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private WorkItem persist(WorkItemStatus status, Instant expiresAt, Instant claimDeadline) {
        WorkItem wi = WorkItem.builder()
                              .title("Test")
                              .status(status)
                              .priority(WorkItemPriority.MEDIUM)
                              .createdAt(Instant.now())
                              .updatedAt(Instant.now())
                              .expiresAt(expiresAt)
                              .claimDeadline(claimDeadline)
                              .build();
        return workItemStore.put(wi);
    }

    // -------------------------------------------------------------------------
    // findExpired
    // -------------------------------------------------------------------------

    @Test
    void findExpired_returnsItemWithPastExpiryAndPendingStatus() {
        WorkItem target = persist(WorkItemStatus.PENDING, Instant.now().minusSeconds(3600), null);
        List<WorkItem> result = workItemStore.scan(WorkItemQuery.expired(Instant.now()));
        assertThat(result).extracting(WorkItem::id).contains(target.id());
    }

    @Test
    void findExpired_returnsItemWithPastExpiryAndAssignedStatus() {
        WorkItem target = persist(WorkItemStatus.ASSIGNED, Instant.now().minusSeconds(3600), null);
        List<WorkItem> result = workItemStore.scan(WorkItemQuery.expired(Instant.now()));
        assertThat(result).extracting(WorkItem::id).contains(target.id());
    }

    @Test
    void findExpired_returnsItemWithPastExpiryAndInProgressStatus() {
        WorkItem target = persist(WorkItemStatus.IN_PROGRESS, Instant.now().minusSeconds(3600), null);
        List<WorkItem> result = workItemStore.scan(WorkItemQuery.expired(Instant.now()));
        assertThat(result).extracting(WorkItem::id).contains(target.id());
    }

    @Test
    void findExpired_returnsItemWithPastExpiryAndSuspendedStatus() {
        // CRITICAL: SUSPENDED must be included in findExpired — items cannot wait forever in suspension
        WorkItem target = persist(WorkItemStatus.SUSPENDED, Instant.now().minusSeconds(3600), null);
        List<WorkItem> result = workItemStore.scan(WorkItemQuery.expired(Instant.now()));
        assertThat(result).extracting(WorkItem::id).contains(target.id());
    }

    @Test
    void findExpired_doesNotReturnCompletedItem() {
        WorkItem target = persist(WorkItemStatus.COMPLETED, Instant.now().minusSeconds(3600), null);
        List<WorkItem> result = workItemStore.scan(WorkItemQuery.expired(Instant.now()));
        assertThat(result).extracting(WorkItem::id).doesNotContain(target.id());
    }

    @Test
    void findExpired_doesNotReturnCancelledItem() {
        WorkItem target = persist(WorkItemStatus.CANCELLED, Instant.now().minusSeconds(3600), null);
        List<WorkItem> result = workItemStore.scan(WorkItemQuery.expired(Instant.now()));
        assertThat(result).extracting(WorkItem::id).doesNotContain(target.id());
    }

    @Test
    void findExpired_doesNotReturnFutureExpiry() {
        WorkItem target = persist(WorkItemStatus.PENDING, Instant.now().plusSeconds(3600), null);
        List<WorkItem> result = workItemStore.scan(WorkItemQuery.expired(Instant.now()));
        assertThat(result).extracting(WorkItem::id).doesNotContain(target.id());
    }

    @Test
    void findExpired_nullExpiresAt_notReturned() {
        WorkItem target = persist(WorkItemStatus.PENDING, null, null);
        List<WorkItem> result = workItemStore.scan(WorkItemQuery.expired(Instant.now()));
        assertThat(result).extracting(WorkItem::id).doesNotContain(target.id());
    }

    // -------------------------------------------------------------------------
    // findUnclaimedPastDeadline
    // -------------------------------------------------------------------------

    @Test
    void findUnclaimedPastDeadline_returnsPendingWithPastDeadline() {
        WorkItem target = persist(WorkItemStatus.PENDING, null, Instant.now().minusSeconds(3600));
        List<WorkItem> result = workItemStore.scan(WorkItemQuery.claimExpired(Instant.now()));
        assertThat(result).extracting(WorkItem::id).contains(target.id());
    }

    @Test
    void findUnclaimedPastDeadline_doesNotReturnAssigned() {
        // Already claimed — claim deadline no longer relevant
        WorkItem target = persist(WorkItemStatus.ASSIGNED, null, Instant.now().minusSeconds(3600));
        List<WorkItem> result = workItemStore.scan(WorkItemQuery.claimExpired(Instant.now()));
        assertThat(result).extracting(WorkItem::id).doesNotContain(target.id());
    }

    @Test
    void findUnclaimedPastDeadline_doesNotReturnFutureDeadline() {
        WorkItem target = persist(WorkItemStatus.PENDING, null, Instant.now().plusSeconds(3600));
        List<WorkItem> result = workItemStore.scan(WorkItemQuery.claimExpired(Instant.now()));
        assertThat(result).extracting(WorkItem::id).doesNotContain(target.id());
    }

    @Test
    void findUnclaimedPastDeadline_nullClaimDeadline_notReturned() {
        WorkItem target = persist(WorkItemStatus.PENDING, null, null);
        List<WorkItem> result = workItemStore.scan(WorkItemQuery.claimExpired(Instant.now()));
        assertThat(result).extracting(WorkItem::id).doesNotContain(target.id());
    }

    // -------------------------------------------------------------------------
    // findInbox JPA LIKE queries
    // -------------------------------------------------------------------------

    @Test
    void findInbox_JPA_byAssigneeId() {
        WorkItem target = persist(WorkItemStatus.ASSIGNED, null, null);
        target = workItemStore.put(target.toBuilder().assigneeId("alice").build());

        List<WorkItem> result = workItemStore.scan(WorkItemQuery.inbox("alice", null, null));
        assertThat(result).extracting(WorkItem::id).contains(target.id());
    }

    @Test
    void findInbox_JPA_byCandidateGroupsLike() {
        WorkItem target = persist(WorkItemStatus.PENDING, null, null);
        target = workItemStore.put(target.toBuilder().candidateGroups("team-a,team-b").build());

        List<WorkItem> result = workItemStore.scan(WorkItemQuery.inbox(null, List.of("team-a"), null));
        assertThat(result).extracting(WorkItem::id).contains(target.id());
    }

    @Test
    void findInbox_JPA_byMultipleCandidateGroups_OR() {
        WorkItem target = persist(WorkItemStatus.PENDING, null, null);
        target = workItemStore.put(target.toBuilder().candidateGroups("team-c").build());

        // "team-a" does not match, "team-c" does — OR logic must find it
        List<WorkItem> result = workItemStore.scan(WorkItemQuery.inbox(null, List.of("team-a", "team-c"), null));
        assertThat(result).extracting(WorkItem::id).contains(target.id());
    }

    @Test
    void findInbox_JPA_byCandidateUsersLike() {
        WorkItem target = persist(WorkItemStatus.PENDING, null, null);
        target = workItemStore.put(target.toBuilder().candidateUsers("bob,carol").build());

        List<WorkItem> result = workItemStore.scan(WorkItemQuery.inbox("bob", null, null));
        assertThat(result).extracting(WorkItem::id).contains(target.id());
    }

    @Test
    void findInbox_JPA_statusFilter() {
        WorkItem target = persist(WorkItemStatus.PENDING, null, null);
        target = workItemStore.put(target.toBuilder().assigneeId("alice").build());

        // Filtering for ASSIGNED should exclude the PENDING item
        List<WorkItem> result = workItemStore.scan(
                WorkItemQuery.inbox("alice", null, null).toBuilder().status(WorkItemStatus.ASSIGNED).build());
        assertThat(result).extracting(WorkItem::id).doesNotContain(target.id());
    }

    @Test
    void findInbox_JPA_noActorFilter_returnsAll() {
        // With the new KV-native scan() semantics, no assignment criteria = no assignment constraint
        // inbox(null, [], null) is equivalent to all() — no actor filter applied
        WorkItem target = persist(WorkItemStatus.PENDING, null, null);

        List<WorkItem> result = workItemStore.scan(WorkItemQuery.inbox(null, List.of(), null));
        assertThat(result).extracting(WorkItem::id).contains(target.id());
    }

    // -------------------------------------------------------------------------
    // findByLabelPattern
    // -------------------------------------------------------------------------

    @Test
    @jakarta.transaction.Transactional
    void findByLabelPattern_exactMatch_returnMatchingItems() {
        var wi = WorkItem.builder()
                         .title("label-test-exact")
                         .status(WorkItemStatus.PENDING)
                         .priority(WorkItemPriority.MEDIUM)
                         .labels(List.of(new WorkItemLabel("legal/contracts", LabelPersistence.MANUAL, "alice")))
                         .build();
        workItemStore.put(wi);

        var results = workItemStore.scan(WorkItemQuery.byLabelPattern("legal/contracts"));

        assertThat(results).extracting(WorkItem::title).contains("label-test-exact");
    }

    @Test
    @jakarta.transaction.Transactional
    void findByLabelPattern_singleWildcard_matchesOneLevel() {
        var wi = WorkItem.builder()
                         .title("label-test-wildcard")
                         .status(WorkItemStatus.PENDING)
                         .priority(WorkItemPriority.MEDIUM)
                         .labels(List.of(new WorkItemLabel("legal/contracts", LabelPersistence.MANUAL, "alice")))
                         .build();
        workItemStore.put(wi);

        assertThat(workItemStore.scan(WorkItemQuery.byLabelPattern("legal/*")))
                .extracting(WorkItem::title).contains("label-test-wildcard");

        assertThat(workItemStore.scan(WorkItemQuery.byLabelPattern("legal/contracts/*")))
                .extracting(WorkItem::title).doesNotContain("label-test-wildcard");
    }

    @Test
    @jakarta.transaction.Transactional
    void findByLabelPattern_multiWildcard_matchesAllDepths() {
        var wi1 = WorkItem.builder()
                          .title("label-test-multi-1")
                          .status(WorkItemStatus.PENDING)
                          .priority(WorkItemPriority.MEDIUM)
                          .labels(List.of(new WorkItemLabel("legal/contracts", LabelPersistence.MANUAL, "alice")))
                          .build();
        workItemStore.put(wi1);

        var wi2 = WorkItem.builder()
                          .title("label-test-multi-2")
                          .status(WorkItemStatus.PENDING)
                          .priority(WorkItemPriority.MEDIUM)
                          .labels(List.of(new WorkItemLabel("legal/contracts/nda", LabelPersistence.MANUAL, "alice")))
                          .build();
        workItemStore.put(wi2);

        assertThat(workItemStore.scan(WorkItemQuery.byLabelPattern("legal/**")))
                .extracting(WorkItem::title)
                .contains("label-test-multi-1", "label-test-multi-2");
    }

    @Test
    @jakarta.transaction.Transactional
    void findByLabelPattern_noMatch_returnsEmpty() {
        assertThat(workItemStore.scan(WorkItemQuery.byLabelPattern("nonexistent/path"))).isEmpty();
    }
}
