package io.casehub.work.mongodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.persistence.OptimisticLockException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.work.api.DeclineTarget;
import io.casehub.work.api.LabelPersistence;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemLabel;
import io.casehub.work.runtime.model.WorkItemPriority;
import io.casehub.work.runtime.model.WorkItemStatus;
import io.casehub.work.runtime.repository.WorkItemQuery;
import io.casehub.work.runtime.repository.WorkItemStore;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class MongoWorkItemStoreTest {

    @Inject
    WorkItemStore store;

    @Inject
    MutableCurrentPrincipal principal;

    @BeforeEach
    void clearAll() {
        principal.reset();
        MongoWorkItemDocument.deleteAll();
    }

    // ── Put / Get ─────────────────────────────────────────────────────────────

    @Test
    void put_assignsId_whenAbsent() {
        final WorkItem wi = pending("alice", "Assign ID test");
        assertThat(wi.id).isNull();

        store.put(wi);

        assertThat(wi.id).isNotNull();
    }

    @Test
    void put_setsTimestamps() {
        final WorkItem wi = pending("alice", "Timestamps test");
        final Instant before = Instant.now().minusSeconds(1);

        store.put(wi);

        assertThat(wi.createdAt).isAfter(before);
        assertThat(wi.updatedAt).isAfterOrEqualTo(wi.createdAt);
    }

    @Test
    void put_and_get_roundtrip() {
        final WorkItem wi = pending("alice", "Roundtrip test");
        wi.description = "Do something important";
        wi.category = "review";
        wi.priority = WorkItemPriority.HIGH;
        wi.formKey = "review-form";
        wi.payload = "{\"ref\":\"PROJ-42\"}";

        store.put(wi);
        final Optional<WorkItem> found = store.get(wi.id);

        assertThat(found).isPresent();
        final WorkItem loaded = found.get();
        assertThat(loaded.title).isEqualTo("Roundtrip test");
        assertThat(loaded.description).isEqualTo("Do something important");
        assertThat(loaded.category).isEqualTo("review");
        assertThat(loaded.priority).isEqualTo(WorkItemPriority.HIGH);
        assertThat(loaded.formKey).isEqualTo("review-form");
        assertThat(loaded.payload).isEqualTo("{\"ref\":\"PROJ-42\"}");
        assertThat(loaded.status).isEqualTo(WorkItemStatus.PENDING);
        assertThat(loaded.createdBy).isEqualTo("alice");
    }

    @Test
    void get_returnsEmpty_whenNotFound() {
        assertThat(store.get(java.util.UUID.randomUUID())).isEmpty();
    }

    @Test
    void put_updatesExisting_onSecondCall() {
        final WorkItem wi = pending("alice", "Update test");
        store.put(wi);

        wi.status = WorkItemStatus.ASSIGNED;
        wi.assigneeId = "bob";
        store.put(wi);

        final WorkItem loaded = store.get(wi.id).orElseThrow();
        assertThat(loaded.status).isEqualTo(WorkItemStatus.ASSIGNED);
        assertThat(loaded.assigneeId).isEqualTo("bob");
        assertThat(MongoWorkItemDocument.count()).isEqualTo(1);
    }

    // ── ScanAll ───────────────────────────────────────────────────────────────

    @Test
    void scanAll_returnsAllDocuments() {
        store.put(pending("alice", "Item A"));
        store.put(pending("bob", "Item B"));
        store.put(pending("carol", "Item C"));

        assertThat(store.scanAll()).hasSize(3);
    }

    // ── Inbox (assignment OR) ─────────────────────────────────────────────────

    @Test
    void scan_inbox_byAssigneeId() {
        final WorkItem wi = pending("alice", "Direct assignee");
        wi.assigneeId = "alice";
        store.put(wi);
        store.put(pending("bob", "Other item"));

        final List<WorkItem> results = store.scan(WorkItemQuery.inbox("alice", null, null));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).title).isEqualTo("Direct assignee");
    }

    @Test
    void scan_inbox_byCandidateGroup() {
        final WorkItem wi = pending("system", "Group item");
        wi.candidateGroups = "finance-team,hr-team";
        store.put(wi);
        store.put(pending("system", "Other item"));

        final List<WorkItem> results = store.scan(
                WorkItemQuery.inbox(null, List.of("finance-team"), null));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).title).isEqualTo("Group item");
    }

    @Test
    void scan_inbox_byCandidateUsers() {
        final WorkItem wi = pending("system", "Candidate user item");
        wi.candidateUsers = "alice,bob";
        store.put(wi);
        store.put(pending("system", "Other"));

        final List<WorkItem> results = store.scan(WorkItemQuery.inbox(null, null, "alice"));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).title).isEqualTo("Candidate user item");
    }

    @Test
    void scan_inbox_orLogic_matchesAnyDimension() {
        final WorkItem wi = pending("system", "Multi-candidate item");
        wi.candidateGroups = "legal-team";
        wi.candidateUsers = "dave";
        store.put(wi);

        // alice is in legal-team → matches via group
        assertThat(store.scan(WorkItemQuery.inbox(null, List.of("legal-team"), null))).hasSize(1);
        // dave matches via candidateUsers
        assertThat(store.scan(WorkItemQuery.inbox(null, null, "dave"))).hasSize(1);
        // carol is not in any dimension → no match
        assertThat(store.scan(WorkItemQuery.inbox("carol", null, null))).hasSize(0);
    }

    // ── Status / Priority / Category filters ──────────────────────────────────

    @Test
    void scan_byStatus_exactMatch() {
        store.put(withStatus("alice", "Pending", WorkItemStatus.PENDING));
        store.put(withStatus("alice", "Assigned", WorkItemStatus.ASSIGNED));

        assertThat(store.scan(WorkItemQuery.builder().status(WorkItemStatus.PENDING).build())).hasSize(1);
        assertThat(store.scan(WorkItemQuery.builder().status(WorkItemStatus.ASSIGNED).build())).hasSize(1);
    }

    @Test
    void scan_byStatusIn() {
        store.put(withStatus("alice", "Pending", WorkItemStatus.PENDING));
        store.put(withStatus("alice", "InProgress", WorkItemStatus.IN_PROGRESS));
        store.put(withStatus("alice", "Completed", WorkItemStatus.COMPLETED));

        final List<WorkItem> results = store.scan(WorkItemQuery.builder()
                .statusIn(List.of(WorkItemStatus.PENDING, WorkItemStatus.IN_PROGRESS))
                .build());

        assertThat(results).hasSize(2)
                .extracting(w -> w.title)
                .containsExactlyInAnyOrder("Pending", "InProgress");
    }

    @Test
    void scan_byPriority() {
        final WorkItem hi = pending("alice", "High prio");
        hi.priority = WorkItemPriority.HIGH;
        store.put(hi);

        final WorkItem lo = pending("alice", "Low prio");
        lo.priority = WorkItemPriority.LOW;
        store.put(lo);

        assertThat(store.scan(WorkItemQuery.builder().priority(WorkItemPriority.HIGH).build()))
                .hasSize(1).first().extracting(w -> w.title).isEqualTo("High prio");
    }

    @Test
    void scan_byCategory() {
        final WorkItem wi = pending("alice", "Finance item");
        wi.category = "finance";
        store.put(wi);
        store.put(pending("alice", "No category"));

        assertThat(store.scan(WorkItemQuery.builder().category("finance").build())).hasSize(1);
        assertThat(store.scan(WorkItemQuery.builder().category("legal").build())).hasSize(0);
    }

    // ── Expired / ClaimExpired ────────────────────────────────────────────────

    @Test
    void scan_expired_returnsActiveItemsPastDeadline() {
        final Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
        final Instant future = Instant.now().plus(1, ChronoUnit.HOURS);

        final WorkItem expired = withStatus("alice", "Expired", WorkItemStatus.PENDING);
        expired.expiresAt = past;
        store.put(expired);

        final WorkItem active = withStatus("alice", "Active", WorkItemStatus.PENDING);
        active.expiresAt = future;
        store.put(active);

        final WorkItem noDeadline = withStatus("alice", "No deadline", WorkItemStatus.PENDING);
        store.put(noDeadline);

        final List<WorkItem> results = store.scan(WorkItemQuery.expired(Instant.now()));
        assertThat(results).hasSize(1);
        assertThat(results.get(0).title).isEqualTo("Expired");
    }

    @Test
    void scan_expired_excludesTerminalStatuses() {
        final Instant past = Instant.now().minus(1, ChronoUnit.HOURS);

        final WorkItem completed = withStatus("alice", "Completed past deadline", WorkItemStatus.COMPLETED);
        completed.expiresAt = past;
        store.put(completed);

        assertThat(store.scan(WorkItemQuery.expired(Instant.now()))).hasSize(0);
    }

    @Test
    void scan_claimExpired_returnsPendingPastClaimDeadline() {
        final Instant past = Instant.now().minus(1, ChronoUnit.HOURS);

        final WorkItem claimExpired = withStatus("alice", "Claim expired", WorkItemStatus.PENDING);
        claimExpired.claimDeadline = past;
        store.put(claimExpired);

        final WorkItem assigned = withStatus("alice", "Assigned", WorkItemStatus.ASSIGNED);
        assigned.claimDeadline = past;
        store.put(assigned);

        final List<WorkItem> results = store.scan(WorkItemQuery.claimExpired(Instant.now()));
        assertThat(results).hasSize(1);
        assertThat(results.get(0).title).isEqualTo("Claim expired");
    }

    // ── Label pattern ─────────────────────────────────────────────────────────

    @Test
    void scan_byLabelPattern_exactMatch() {
        store.put(withLabel("alice", "Exact match", "legal/contracts"));
        store.put(withLabel("alice", "No match", "legal/ip"));
        store.put(pending("alice", "No labels"));

        assertThat(store.scan(WorkItemQuery.byLabelPattern("legal/contracts"))).hasSize(1);
    }

    @Test
    void scan_byLabelPattern_singleWildcard() {
        store.put(withLabel("alice", "Direct child", "legal/contracts"));
        store.put(withLabel("alice", "Deep child", "legal/contracts/nda"));
        store.put(withLabel("alice", "Other branch", "finance/budget"));

        final List<WorkItem> results = store.scan(WorkItemQuery.byLabelPattern("legal/*"));
        assertThat(results).hasSize(1);
        assertThat(results.get(0).title).isEqualTo("Direct child");
    }

    @Test
    void scan_byLabelPattern_doubleWildcard() {
        store.put(withLabel("alice", "Direct child", "legal/contracts"));
        store.put(withLabel("alice", "Deep child", "legal/contracts/nda"));
        store.put(withLabel("alice", "Other branch", "finance/budget"));

        final List<WorkItem> results = store.scan(WorkItemQuery.byLabelPattern("legal/**"));
        assertThat(results).hasSize(2);
    }

    // ── Label field roundtrip ─────────────────────────────────────────────────

    @Test
    void labels_roundtrip_preservesAllFields() {
        final WorkItem wi = pending("alice", "Label roundtrip");
        final WorkItemLabel label = new WorkItemLabel("legal/contracts", LabelPersistence.MANUAL, "alice");
        wi.labels = List.of(label);

        store.put(wi);
        final WorkItem loaded = store.get(wi.id).orElseThrow();

        assertThat(loaded.labels).hasSize(1);
        assertThat(loaded.labels.get(0).path).isEqualTo("legal/contracts");
        assertThat(loaded.labels.get(0).persistence).isEqualTo(LabelPersistence.MANUAL);
        assertThat(loaded.labels.get(0).appliedBy).isEqualTo("alice");
    }

    @Test
    void scan_byOutcome_filtersCorrectly() {
        final WorkItem approved = pending("alice", "Approved item");
        approved.status = WorkItemStatus.COMPLETED;
        approved.outcome = "approved";
        store.put(approved);

        final WorkItem rejected = pending("alice", "Rejected item");
        rejected.status = WorkItemStatus.REJECTED;
        rejected.outcome = "rejected";
        store.put(rejected);

        final WorkItem noOutcome = pending("alice", "No outcome");
        store.put(noOutcome);

        final List<WorkItem> results = store.scan(
                WorkItemQuery.builder().outcome("approved").build());

        assertThat(results).hasSize(1);
        assertThat(results.get(0).title).isEqualTo("Approved item");
    }

    @Test
    void candidateGroups_roundtrip_commaSeparatedPreserved() {
        final WorkItem wi = pending("system", "Group routing");
        wi.candidateGroups = "finance-team,hr-team";

        store.put(wi);
        final WorkItem loaded = store.get(wi.id).orElseThrow();

        assertThat(loaded.candidateGroups).isEqualTo("finance-team,hr-team");
    }

    // ── Query method overrides ───────────────────────────────────────────────

    @Test
    void findByCallerRef_returnsMatching() {
        final WorkItem wi = pending("alice", "CallerRef match");
        wi.callerRef = "case:abc-123/pi:step-7";
        store.put(wi);

        store.put(pending("bob", "No callerRef"));

        final java.util.Optional<WorkItem> result = store.findByCallerRef("case:abc-123/pi:step-7");
        assertThat(result).isPresent();
        assertThat(result.get().title).isEqualTo("CallerRef match");
    }

    @Test
    void findByCallerRef_returnsEmpty_whenNotFound() {
        final WorkItem wi = pending("alice", "CallerRef item");
        wi.callerRef = "case:abc-123/pi:step-7";
        store.put(wi);

        assertThat(store.findByCallerRef("case:nonexistent/pi:nope")).isEmpty();
    }

    @Test
    void findByParentId_returnsChildren() {
        final UUID parentId = UUID.randomUUID();

        final WorkItem child1 = pending("alice", "Child 1");
        child1.parentId = parentId;
        store.put(child1);

        final WorkItem child2 = pending("alice", "Child 2");
        child2.parentId = parentId;
        store.put(child2);

        store.put(pending("alice", "Standalone"));

        final List<WorkItem> children = store.findByParentId(parentId);
        assertThat(children).hasSize(2)
                .extracting(w -> w.title)
                .containsExactlyInAnyOrder("Child 1", "Child 2");
    }

    @Test
    void findByParentIdExcludingStatuses_excludesTerminal() {
        final UUID parentId = UUID.randomUUID();

        final WorkItem active = pending("alice", "Active child");
        active.parentId = parentId;
        store.put(active);

        final WorkItem completed = withStatus("alice", "Completed child", WorkItemStatus.COMPLETED);
        completed.parentId = parentId;
        store.put(completed);

        final WorkItem faulted = withStatus("alice", "Faulted child", WorkItemStatus.FAULTED);
        faulted.parentId = parentId;
        store.put(faulted);

        final List<WorkItem> results = store.findByParentIdExcludingStatuses(parentId,
                List.of(WorkItemStatus.COMPLETED, WorkItemStatus.FAULTED));
        assertThat(results).hasSize(1);
        assertThat(results.get(0).title).isEqualTo("Active child");
    }

    @Test
    void findByParentIdWithStatuses_filtersToGivenStatuses() {
        final UUID parentId = UUID.randomUUID();

        final WorkItem pending = pending("alice", "Pending child");
        pending.parentId = parentId;
        store.put(pending);

        final WorkItem assigned = withStatus("alice", "Assigned child", WorkItemStatus.ASSIGNED);
        assigned.parentId = parentId;
        store.put(assigned);

        final WorkItem completed = withStatus("alice", "Completed child", WorkItemStatus.COMPLETED);
        completed.parentId = parentId;
        store.put(completed);

        final List<WorkItem> results = store.findByParentIdWithStatuses(parentId,
                List.of(WorkItemStatus.PENDING, WorkItemStatus.ASSIGNED));
        assertThat(results).hasSize(2)
                .extracting(w -> w.title)
                .containsExactlyInAnyOrder("Pending child", "Assigned child");
    }

    @Test
    void countByParentAndAssignee_excludesSelf() {
        final UUID parentId = UUID.randomUUID();

        final WorkItem child1 = withStatus("alice", "Child 1", WorkItemStatus.ASSIGNED);
        child1.parentId = parentId;
        child1.assigneeId = "bob";
        store.put(child1);

        final WorkItem child2 = withStatus("alice", "Child 2", WorkItemStatus.IN_PROGRESS);
        child2.parentId = parentId;
        child2.assigneeId = "bob";
        store.put(child2);

        final WorkItem completedChild = withStatus("alice", "Completed", WorkItemStatus.COMPLETED);
        completedChild.parentId = parentId;
        completedChild.assigneeId = "bob";
        store.put(completedChild);

        // Count bob's non-terminal instances excluding child1
        final long count = store.countByParentAndAssignee(parentId, "bob", child1.id);
        assertThat(count).isEqualTo(1L); // only child2 — child1 excluded, completedChild is terminal
    }

    // ── Tenant isolation ─────────────────────────────────────────────────────

    @Test
    void queryMethods_tenantIsolation() {
        principal.setTenancyId("tenant-a");
        final UUID parentId = UUID.randomUUID();

        final WorkItem wiA = pending("alice", "Tenant A item");
        wiA.callerRef = "case:shared-ref/pi:step-1";
        wiA.parentId = parentId;
        wiA.assigneeId = "bob";
        store.put(wiA);

        // Switch to tenant B — should not see tenant A's data
        principal.setTenancyId("tenant-b");

        assertThat(store.findByCallerRef("case:shared-ref/pi:step-1")).isEmpty();
        assertThat(store.findByParentId(parentId)).isEmpty();
        assertThat(store.findByParentIdExcludingStatuses(parentId,
                List.of(WorkItemStatus.COMPLETED))).isEmpty();
        assertThat(store.findByParentIdWithStatuses(parentId,
                List.of(WorkItemStatus.PENDING))).isEmpty();
        assertThat(store.countByParentAndAssignee(parentId, "bob", UUID.randomUUID()))
                .isEqualTo(0L);
    }

    @Test
    void put_and_get_roundtrip_previouslyMissingFields() {
        final WorkItem wi = pending("alice", "Full roundtrip");
        wi.description = "Full field coverage";
        wi.category = "review";
        wi.priority = WorkItemPriority.HIGH;
        wi.formKey = "review-form";
        wi.payload = "{\"ref\":\"PROJ-42\"}";
        wi.resolution = "done";
        wi.owner = "team-lead";
        wi.candidateGroups = "finance-team,hr-team";
        wi.candidateUsers = "bob,carol";
        wi.requiredCapabilities = "java,review";
        wi.delegationChain = "alice>bob";
        wi.delegationDeclineTarget = DeclineTarget.POOL;
        wi.priorStatus = WorkItemStatus.ASSIGNED;
        wi.claimDeadline = Instant.parse("2026-07-01T12:00:00Z");
        wi.expiresAt = Instant.parse("2026-07-15T12:00:00Z");
        wi.followUpDate = Instant.parse("2026-07-05T12:00:00Z");
        wi.assignedAt = Instant.parse("2026-06-20T10:00:00Z");
        wi.startedAt = Instant.parse("2026-06-20T11:00:00Z");
        wi.suspendedAt = Instant.parse("2026-06-20T15:00:00Z");

        // The 13 previously-missing fields
        wi.accumulatedUnclaimedSeconds = 3600L;
        wi.lastReturnedToPoolAt = Instant.parse("2026-06-20T09:00:00Z");
        wi.confidenceScore = 0.85;
        wi.callerRef = "case:abc-123/pi:step-7";
        wi.parentId = UUID.randomUUID();
        wi.scope = "casehubio/devtown/pr-review";
        wi.templateId = UUID.randomUUID();
        wi.permittedOutcomes = "[\"approved\",\"rejected\",\"needs-revision\"]";
        wi.excludedUsers = "dave,eve";
        wi.outcome = "approved";
        wi.inputDataSchema = "{\"type\":\"object\"}";
        wi.outputDataSchema = "{\"type\":\"string\"}";

        store.put(wi);
        final WorkItem loaded = store.get(wi.id).orElseThrow();

        // Original fields
        assertThat(loaded.description).isEqualTo("Full field coverage");
        assertThat(loaded.category).isEqualTo("review");
        assertThat(loaded.priority).isEqualTo(WorkItemPriority.HIGH);
        assertThat(loaded.formKey).isEqualTo("review-form");
        assertThat(loaded.payload).isEqualTo("{\"ref\":\"PROJ-42\"}");
        assertThat(loaded.resolution).isEqualTo("done");
        assertThat(loaded.owner).isEqualTo("team-lead");
        assertThat(loaded.candidateGroups).isEqualTo("finance-team,hr-team");
        assertThat(loaded.candidateUsers).isEqualTo("bob,carol");
        assertThat(loaded.delegationDeclineTarget).isEqualTo(DeclineTarget.POOL);
        assertThat(loaded.priorStatus).isEqualTo(WorkItemStatus.ASSIGNED);

        // The 13 previously-missing fields
        assertThat(loaded.accumulatedUnclaimedSeconds).isEqualTo(3600L);
        assertThat(loaded.lastReturnedToPoolAt).isEqualTo(Instant.parse("2026-06-20T09:00:00Z"));
        assertThat(loaded.confidenceScore).isEqualTo(0.85);
        assertThat(loaded.callerRef).isEqualTo("case:abc-123/pi:step-7");
        assertThat(loaded.parentId).isEqualTo(wi.parentId);
        assertThat(loaded.scope).isEqualTo("casehubio/devtown/pr-review");
        assertThat(loaded.templateId).isEqualTo(wi.templateId);
        assertThat(loaded.permittedOutcomes).isEqualTo("[\"approved\",\"rejected\",\"needs-revision\"]");
        assertThat(loaded.excludedUsers).isEqualTo("dave,eve");
        assertThat(loaded.outcome).isEqualTo("approved");
        assertThat(loaded.inputDataSchema).isEqualTo("{\"type\":\"object\"}");
        assertThat(loaded.outputDataSchema).isEqualTo("{\"type\":\"string\"}");
    }

    // ── OCC (Optimistic Concurrency Control) ─────────────────────────────────

    @Test
    void put_setsVersionToZero_onInsert() {
        final WorkItem wi = pending("alice", "Version insert test");
        store.put(wi);

        assertThat(wi.version).isEqualTo(0L);

        final WorkItem loaded = store.get(wi.id).orElseThrow();
        assertThat(loaded.version).isEqualTo(0L);
    }

    @Test
    void put_incrementsVersion_onUpdate() {
        final WorkItem wi = pending("alice", "Version update test");
        store.put(wi);
        assertThat(wi.version).isEqualTo(0L);

        wi.status = WorkItemStatus.ASSIGNED;
        wi.assigneeId = "bob";
        store.put(wi);
        assertThat(wi.version).isEqualTo(1L);

        final WorkItem loaded = store.get(wi.id).orElseThrow();
        assertThat(loaded.version).isEqualTo(1L);
        assertThat(loaded.status).isEqualTo(WorkItemStatus.ASSIGNED);
    }

    @Test
    void put_throwsOptimisticLockException_onStaleVersion() {
        final WorkItem wi = pending("alice", "OCC test");
        store.put(wi);

        // Two readers get the same version
        final WorkItem reader1 = store.get(wi.id).orElseThrow();
        final WorkItem reader2 = store.get(wi.id).orElseThrow();

        assertThat(reader1.version).isEqualTo(0L);
        assertThat(reader2.version).isEqualTo(0L);

        // Reader 1 updates successfully
        reader1.status = WorkItemStatus.ASSIGNED;
        reader1.assigneeId = "bob";
        store.put(reader1);
        assertThat(reader1.version).isEqualTo(1L);

        // Reader 2 attempts to update with stale version — should fail
        reader2.status = WorkItemStatus.ASSIGNED;
        reader2.assigneeId = "carol";
        assertThatThrownBy(() -> store.put(reader2))
                .isInstanceOf(OptimisticLockException.class)
                .hasMessageContaining("Version conflict");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private WorkItem pending(final String createdBy, final String title) {
        final WorkItem wi = new WorkItem();
        wi.title = title;
        wi.createdBy = createdBy;
        wi.status = WorkItemStatus.PENDING;
        return wi;
    }

    private WorkItem withStatus(final String createdBy, final String title, final WorkItemStatus status) {
        final WorkItem wi = pending(createdBy, title);
        wi.status = status;
        return wi;
    }

    private WorkItem withLabel(final String createdBy, final String title, final String labelPath) {
        final WorkItem wi = pending(createdBy, title);
        wi.labels = new java.util.ArrayList<>();
        wi.labels.add(new WorkItemLabel(labelPath, LabelPersistence.MANUAL, createdBy));
        return wi;
    }
}
