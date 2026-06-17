package io.casehub.work.mongodb;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.work.issuetracker.model.WorkItemIssueLink;
import io.casehub.work.issuetracker.repository.IssueLinkStore;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class MongoIssueLinkStoreTest {

    @Inject
    IssueLinkStore store;

    @Inject
    MutableCurrentPrincipal principal;

    @BeforeEach
    void clearAll() {
        principal.reset();
        MongoIssueLinkDocument.deleteAll();
    }

    // ── Save and FindById ──────────────────────────────────────────────────────

    @Test
    void save_and_findById_roundtrip() {
        final UUID workItemId = UUID.randomUUID();
        final WorkItemIssueLink link = link(workItemId, "github", "owner/repo#42");
        link.title = "Fix login bug";
        link.url = "https://github.com/owner/repo/issues/42";
        link.status = "open";
        link.linkedBy = "alice";

        assertThat(link.id).isNull();
        assertThat(link.linkedAt).isNull();

        store.save(link);

        assertThat(link.id).isNotNull();
        assertThat(link.linkedAt).isNotNull();

        final Optional<WorkItemIssueLink> found = store.findById(link.id);

        assertThat(found).isPresent();
        final WorkItemIssueLink loaded = found.get();
        assertThat(loaded.id).isEqualTo(link.id);
        assertThat(loaded.workItemId).isEqualTo(workItemId);
        assertThat(loaded.trackerType).isEqualTo("github");
        assertThat(loaded.externalRef).isEqualTo("owner/repo#42");
        assertThat(loaded.title).isEqualTo("Fix login bug");
        assertThat(loaded.url).isEqualTo("https://github.com/owner/repo/issues/42");
        assertThat(loaded.status).isEqualTo("open");
        assertThat(loaded.linkedBy).isEqualTo("alice");
        assertThat(loaded.linkedAt).isNotNull();
    }

    // ── FindByWorkItemId ──────────────────────────────────────────────────────

    @Test
    void findByWorkItemId() {
        final UUID workItemId = UUID.randomUUID();
        final Instant t1 = Instant.now().minus(2, ChronoUnit.HOURS);
        final Instant t2 = Instant.now().minus(1, ChronoUnit.HOURS);

        final WorkItemIssueLink link1 = link(workItemId, "github", "owner/repo#42");
        link1.linkedAt = t1;
        store.save(link1);

        final WorkItemIssueLink link2 = link(workItemId, "jira", "PROJ-123");
        link2.linkedAt = t2;
        store.save(link2);

        // Different WorkItem
        final WorkItemIssueLink otherLink = link(UUID.randomUUID(), "github", "owner/repo#99");
        store.save(otherLink);

        final List<WorkItemIssueLink> results = store.findByWorkItemId(workItemId);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).externalRef).isEqualTo("owner/repo#42");
        assertThat(results.get(1).externalRef).isEqualTo("PROJ-123");
    }

    // ── FindByRef ─────────────────────────────────────────────────────────────

    @Test
    void findByRef_compoundLookup() {
        final UUID workItemId = UUID.randomUUID();
        final WorkItemIssueLink link1 = link(workItemId, "github", "owner/repo#42");
        store.save(link1);

        final WorkItemIssueLink link2 = link(workItemId, "github", "owner/repo#99");
        store.save(link2);

        final WorkItemIssueLink link3 = link(UUID.randomUUID(), "github", "owner/repo#42");
        store.save(link3);

        final Optional<WorkItemIssueLink> found = store.findByRef(workItemId, "github", "owner/repo#42");

        assertThat(found).isPresent();
        assertThat(found.get().id).isEqualTo(link1.id);
    }

    // ── FindByTrackerRef ──────────────────────────────────────────────────────

    @Test
    void findByTrackerRef_crossWorkItem() {
        final UUID workItem1 = UUID.randomUUID();
        final UUID workItem2 = UUID.randomUUID();

        final WorkItemIssueLink link1 = link(workItem1, "github", "owner/repo#42");
        store.save(link1);

        final WorkItemIssueLink link2 = link(workItem2, "github", "owner/repo#42");
        store.save(link2);

        final WorkItemIssueLink otherLink = link(workItem1, "github", "owner/repo#99");
        store.save(otherLink);

        final List<WorkItemIssueLink> results = store.findByTrackerRef("github", "owner/repo#42");

        assertThat(results).hasSize(2);
        assertThat(results).extracting(l -> l.workItemId).containsExactlyInAnyOrder(workItem1, workItem2);
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_validatesTenanancy() {
        final WorkItemIssueLink link = link(UUID.randomUUID(), "github", "owner/repo#42");
        store.save(link);
        final UUID linkId = link.id;
        final String originalTenancyId = link.tenancyId;

        // Switch to different tenant
        principal.setTenancyId("tenant-2");

        // Attempt delete - should be no-op because tenant doesn't match
        store.delete(link);

        // Switch back and verify link still exists
        principal.setTenancyId(originalTenancyId);
        assertThat(store.findById(linkId)).isPresent();
    }

    // ── Tenant Isolation ──────────────────────────────────────────────────────

    @Test
    void tenantIsolation() {
        final WorkItemIssueLink link = link(UUID.randomUUID(), "github", "owner/repo#42");
        store.save(link);

        principal.setTenancyId("tenant-2");

        assertThat(store.findById(link.id)).isEmpty();
        assertThat(store.findByWorkItemId(link.workItemId)).isEmpty();
        assertThat(store.findByRef(link.workItemId, "github", "owner/repo#42")).isEmpty();
        assertThat(store.findByTrackerRef("github", "owner/repo#42")).isEmpty();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private WorkItemIssueLink link(final UUID workItemId, final String trackerType, final String externalRef) {
        final WorkItemIssueLink link = new WorkItemIssueLink();
        link.workItemId = workItemId;
        link.trackerType = trackerType;
        link.externalRef = externalRef;
        link.status = "unknown";
        link.linkedBy = "test-user";
        return link;
    }
}
