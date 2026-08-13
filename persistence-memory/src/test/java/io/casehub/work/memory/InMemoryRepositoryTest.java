package io.casehub.work.memory;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.work.api.WorkItem;
import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.api.WorkItemQuery;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.runtime.model.AuditEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure JUnit 5 tests for the in-memory store implementations.
 *
 * <p>
 * No {@code @QuarkusTest} — the implementations are plain Java objects and can be
 * constructed and exercised without a CDI container.
 */
class InMemoryRepositoryTest {

    private InMemoryWorkItemStore workItemStore;
    private InMemoryAuditEntryStore auditStore;
    private static final CurrentPrincipal TEST_PRINCIPAL = new CurrentPrincipal() {
        @Override
        public String actorId() {
            return "test-user";
        }

        @Override
        public String tenancyId() {
            return "test-tenant";
        }

        @Override
        public Set<String> groups() {
            return Set.of();
        }

        @Override
        public boolean isCrossTenantAdmin() {
            return false;
        }
    };

    @BeforeEach
    void setUp() {
        workItemStore = new InMemoryWorkItemStore();
        workItemStore.currentPrincipal = TEST_PRINCIPAL;
        auditStore = new InMemoryAuditEntryStore();
        auditStore.currentPrincipal = TEST_PRINCIPAL;
    }

    // =========================================================================
    // WorkItemStore — basic CRUD
    // =========================================================================

    @Test
    void save_assignsUuidIfAbsent() {
        final WorkItem wi = workItem(WorkItemStatus.PENDING);
        assertThat(wi.id()).isNull();

        final WorkItem saved = workItemStore.put(wi);

        assertThat(saved.id()).isNotNull();
    }

    @Test
    void save_returnsPersistedItem() {
        final WorkItem wi    = workItem(WorkItemStatus.PENDING);
        final WorkItem saved = workItemStore.put(wi);

        assertThat(workItemStore.get(saved.id())).isPresent().hasValue(saved);
    }

    @Test
    void findById_absent_returnsEmpty() {
        assertThat(workItemStore.get(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findAll_returnsAllSaved() {
        workItemStore.put(workItem(WorkItemStatus.PENDING));
        workItemStore.put(workItem(WorkItemStatus.ASSIGNED));
        workItemStore.put(workItem(WorkItemStatus.IN_PROGRESS));

        assertThat(workItemStore.scan(WorkItemQuery.all())).hasSize(3);
    }

    @Test
    void clear_removesAll() {
        workItemStore.put(workItem(WorkItemStatus.PENDING));
        workItemStore.put(workItem(WorkItemStatus.ASSIGNED));

        workItemStore.clear();

        assertThat(workItemStore.scan(WorkItemQuery.all())).isEmpty();
    }

    // =========================================================================
    // WorkItemStore — inbox assignment filters
    // =========================================================================

    @Test
    void findInbox_byAssignee() {
        final WorkItem wi = workItem(WorkItemStatus.ASSIGNED).toBuilder().assigneeId("alice").build();
        workItemStore.put(wi);

        final List<WorkItem> result = workItemStore.scan(WorkItemQuery.inbox("alice", null, null));

        assertThat(result).hasSize(1);
    }

    @Test
    void findInbox_byCandidateGroup() {
        final WorkItem wi = workItem(WorkItemStatus.PENDING).toBuilder().candidateGroups("team-a,team-b").build();
        workItemStore.put(wi);

        final List<WorkItem> result = workItemStore.scan(WorkItemQuery.inbox(null, List.of("team-a"), null));

        assertThat(result).hasSize(1);
    }

    @Test
    void findInbox_byCandidateUser_exactMatch() {
        final WorkItem wi = workItem(WorkItemStatus.PENDING).toBuilder().candidateUsers("bob").build();
        workItemStore.put(wi);

        final List<WorkItem> result = workItemStore.scan(WorkItemQuery.inbox("bob", null, null));

        assertThat(result).hasSize(1);
    }

    @Test
    void findInbox_candidateUser_noPartialMatch() {
        final WorkItem wi = workItem(WorkItemStatus.PENDING).toBuilder().candidateUsers("bobby").build();
        workItemStore.put(wi);

        final List<WorkItem> result = workItemStore.scan(WorkItemQuery.inbox("bob", null, null));

        assertThat(result).isEmpty();
    }

    // =========================================================================
    // WorkItemStore — inbox additional filters
    // =========================================================================

    @Test
    void findInbox_statusFilter() {
        final WorkItem wi = workItem(WorkItemStatus.COMPLETED).toBuilder().assigneeId("alice").build();
        workItemStore.put(wi);

        final List<WorkItem> result = workItemStore.scan(
                WorkItemQuery.inbox("alice", null, null).toBuilder().status(WorkItemStatus.PENDING).build());

        assertThat(result).isEmpty();
    }

    @Test
    void scan_byType_exactMatch() {
        final WorkItem wi = workItem(WorkItemStatus.PENDING).toBuilder().types(Set.of("compliance/audit")).build();
        workItemStore.put(wi);

        final List<WorkItem> result = workItemStore.scan(WorkItemQuery.builder().type("compliance/audit").build());
        assertThat(result).hasSize(1);
    }

    @Test
    void scan_byType_ancestorMatch() {
        final WorkItem wi = workItem(WorkItemStatus.PENDING).toBuilder().types(Set.of("compliance/audit")).build();
        workItemStore.put(wi);

        final List<WorkItem> result = workItemStore.scan(WorkItemQuery.builder().type("compliance").build());
        assertThat(result).hasSize(1);
    }

    @Test
    void scan_byType_noMatch() {
        final WorkItem wi = workItem(WorkItemStatus.PENDING).toBuilder().types(Set.of("approval")).build();
        workItemStore.put(wi);

        final List<WorkItem> result = workItemStore.scan(WorkItemQuery.builder().type("compliance").build());
        assertThat(result).isEmpty();
    }

    // =========================================================================
    // WorkItemStore — expiry and deadline queries
    // =========================================================================

    @Test
    void findExpired() {
        final Instant fiveMinutesAgo = Instant.now().minus(5, ChronoUnit.MINUTES);

        final WorkItem expired = workItem(WorkItemStatus.PENDING).toBuilder().expiresAt(fiveMinutesAgo).build();
        workItemStore.put(expired);

        final WorkItem alreadyCompleted = workItem(WorkItemStatus.COMPLETED).toBuilder().expiresAt(fiveMinutesAgo).build();
        workItemStore.put(alreadyCompleted);

        final WorkItem notExpired = workItem(WorkItemStatus.PENDING).toBuilder().expiresAt(Instant.now().plus(1, ChronoUnit.HOURS)).build();
        workItemStore.put(notExpired);

        final List<WorkItem> result = workItemStore.scan(WorkItemQuery.expired(Instant.now()));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo(WorkItemStatus.PENDING);
    }

    @Test
    void findUnclaimedPastDeadline() {
        final Instant fiveMinutesAgo = Instant.now().minus(5, ChronoUnit.MINUTES);

        final WorkItem unclaimed = workItem(WorkItemStatus.PENDING).toBuilder().claimDeadline(fiveMinutesAgo).build();
        workItemStore.put(unclaimed);

        final WorkItem assigned = workItem(WorkItemStatus.ASSIGNED).toBuilder().claimDeadline(fiveMinutesAgo).build();
        workItemStore.put(assigned);

        final WorkItem futureDeadline = workItem(WorkItemStatus.PENDING).toBuilder().claimDeadline(Instant.now().plus(1, ChronoUnit.HOURS)).build();
        workItemStore.put(futureDeadline);

        final List<WorkItem> result = workItemStore.scan(WorkItemQuery.claimExpired(Instant.now()));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo(WorkItemStatus.PENDING);
    }

    // =========================================================================
    // WorkItemStore — concurrency
    // =========================================================================

    @Test
    void put_concurrentWrites_nothingLost() throws Exception {
        final int threads = 8;
        final int perThread = 100;
        final var latch = new java.util.concurrent.CountDownLatch(1);
        final var executor = java.util.concurrent.Executors.newFixedThreadPool(threads);
        final var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();

        for (int t = 0; t < threads; t++) {
            futures.add(executor.submit(() -> {
                try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                for (int i = 0; i < perThread; i++) {
                    workItemStore.put(workItem(WorkItemStatus.PENDING));
                }
            }));
        }
        latch.countDown();
        for (var f : futures) { f.get(5, java.util.concurrent.TimeUnit.SECONDS); }
        executor.shutdown();

        assertThat(workItemStore.findAll()).hasSize(threads * perThread);
    }

    // =========================================================================
    // AuditEntryStore
    // =========================================================================

    @Test
    void append_assignsUuidAndOccurredAt() {
        final AuditEntry entry = auditEntry(UUID.randomUUID(), "CREATED");
        entry.id = null;
        entry.occurredAt = null;

        auditStore.append(entry);

        assertThat(entry.id).isNotNull();
        assertThat(entry.occurredAt).isNotNull();
    }

    @Test
    void findByWorkItemId_returnsOnlyMatchingEntries() {
        final UUID workItemId1 = UUID.randomUUID();
        final UUID workItemId2 = UUID.randomUUID();

        auditStore.append(auditEntry(workItemId1, "CREATED"));
        auditStore.append(auditEntry(workItemId1, "ASSIGNED"));
        auditStore.append(auditEntry(workItemId2, "CREATED"));

        final List<AuditEntry> result = auditStore.findByWorkItemId(workItemId1);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(e -> workItemId1.equals(e.workItemId));
    }

    @Test
    void findByWorkItemId_orderedByOccurredAt() {
        final UUID workItemId = UUID.randomUUID();

        final Instant t1 = Instant.now().minus(10, ChronoUnit.MINUTES);
        final Instant t2 = Instant.now().minus(5, ChronoUnit.MINUTES);
        final Instant t3 = Instant.now();

        // Append in reverse chronological order
        final AuditEntry third = auditEntry(workItemId, "COMPLETED");
        third.occurredAt = t3;
        auditStore.append(third);

        final AuditEntry second = auditEntry(workItemId, "ASSIGNED");
        second.occurredAt = t2;
        auditStore.append(second);

        final AuditEntry first = auditEntry(workItemId, "CREATED");
        first.occurredAt = t1;
        auditStore.append(first);

        final List<AuditEntry> result = auditStore.findByWorkItemId(workItemId);

        assertThat(result).extracting(e -> e.event)
                .containsExactly("CREATED", "ASSIGNED", "COMPLETED");
    }

    // =========================================================================
    // AuditEntryStore — concurrency
    // =========================================================================

    @Test
    void append_concurrentWrites_nothingLost() throws Exception {
        final int threads = 8;
        final int perThread = 50;
        final UUID workItemId = UUID.randomUUID();
        final var latch = new java.util.concurrent.CountDownLatch(1);
        final var executor = java.util.concurrent.Executors.newFixedThreadPool(threads);
        final var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();

        for (int t = 0; t < threads; t++) {
            futures.add(executor.submit(() -> {
                try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                for (int i = 0; i < perThread; i++) {
                    auditStore.append(auditEntry(workItemId, "EVENT"));
                }
            }));
        }
        latch.countDown();
        for (var f : futures) { f.get(5, java.util.concurrent.TimeUnit.SECONDS); }
        executor.shutdown();

        assertThat(auditStore.findByWorkItemId(workItemId)).hasSize(threads * perThread);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private io.casehub.work.api.WorkItem workItem(final WorkItemStatus status) {
        return io.casehub.work.api.WorkItem.builder()
                                           .status(status)
                                           .priority(WorkItemPriority.MEDIUM)
                                           .title("Test")
                                           .createdAt(Instant.now())
                                           .updatedAt(Instant.now())
                                           .build();
    }

    private AuditEntry auditEntry(final UUID workItemId, final String event) {
        final AuditEntry entry = new AuditEntry();
        entry.workItemId = workItemId;
        entry.event = event;
        entry.occurredAt = Instant.now();
        return entry;
    }
}
