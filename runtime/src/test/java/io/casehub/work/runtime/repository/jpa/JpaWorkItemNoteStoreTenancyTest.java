package io.casehub.work.runtime.repository.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemNote;
import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.runtime.repository.WorkItemNoteStore;
import io.casehub.work.runtime.repository.WorkItemStore;
import io.casehub.work.runtime.test.MutableCurrentPrincipal;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Tenant isolation tests for {@link JpaWorkItemNoteStore}.
 *
 * <p>Each test switches between two tenants via {@link MutableCurrentPrincipal} and
 * verifies that queries never leak data across tenant boundaries.
 */
@QuarkusTest
@TestTransaction
class JpaWorkItemNoteStoreTenancyTest {

    private static final String TENANT_A = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String TENANT_B = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

    @Inject
    WorkItemNoteStore store;

    @Inject
    WorkItemStore workItemStore;

    @Inject
    MutableCurrentPrincipal principal;

    @BeforeEach
    void resetPrincipal() {
        principal.reset();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private WorkItem newWorkItem(String title) {
        WorkItem wi = new WorkItem();
        wi.title = title;
        wi.status = WorkItemStatus.PENDING;
        wi.priority = WorkItemPriority.MEDIUM;
        wi.createdAt = Instant.now();
        wi.updatedAt = Instant.now();
        return wi;
    }

    private WorkItemNote newNote(UUID workItemId, String content) {
        WorkItemNote note = new WorkItemNote();
        note.workItemId = workItemId;
        note.content = content;
        note.author = "test-author";
        note.createdAt = Instant.now();
        return note;
    }

    // -------------------------------------------------------------------------
    // append() stamps tenancyId
    // -------------------------------------------------------------------------

    @Test
    void append_stampsTenancyId_whenNull() {
        principal.setTenancyId(TENANT_A);

        // Create work item first
        WorkItem wi = newWorkItem("test-workitem");
        workItemStore.put(wi);

        WorkItemNote note = newNote(wi.id, "test note");
        assertThat(note.tenancyId).isNull();

        store.append(note);

        assertThat(note.tenancyId).isEqualTo(TENANT_A);
    }

    @Test
    void append_preservesTenancyId_whenAlreadySet() {
        principal.setTenancyId(TENANT_B);

        // Create work item first
        WorkItem wi = newWorkItem("test-workitem");
        workItemStore.put(wi);

        WorkItemNote note = newNote(wi.id, "test note");
        note.tenancyId = TENANT_A; // explicitly set to A

        store.append(note);

        // Should keep A, not overwrite with B
        assertThat(note.tenancyId).isEqualTo(TENANT_A);
    }

    // -------------------------------------------------------------------------
    // findById() tenant isolation
    // -------------------------------------------------------------------------

    @Test
    void findById_returnsEmpty_forAnotherTenantNote() {
        // Create work item and note as tenant A
        principal.setTenancyId(TENANT_A);
        WorkItem wi = newWorkItem("tenant-a-workitem");
        workItemStore.put(wi);

        WorkItemNote note = newNote(wi.id, "tenant-a-note");
        store.append(note);
        UUID noteId = note.id;

        // Switch to tenant B — should not see A's note
        principal.setTenancyId(TENANT_B);
        assertThat(store.findById(noteId)).isEmpty();

        // Switch back to A — should see it
        principal.setTenancyId(TENANT_A);
        assertThat(store.findById(noteId)).isPresent();
    }

    // -------------------------------------------------------------------------
    // findByWorkItemId() tenant isolation
    // -------------------------------------------------------------------------

    @Test
    void findByWorkItemId_returnsOnlyCurrentTenantNotes() {
        // Create work item for tenant A
        principal.setTenancyId(TENANT_A);
        WorkItem wiA = newWorkItem("tenant-a-workitem");
        workItemStore.put(wiA);

        // Create note for tenant A
        WorkItemNote noteA = newNote(wiA.id, "note from tenant A");
        store.append(noteA);

        // Create work item for tenant B
        principal.setTenancyId(TENANT_B);
        WorkItem wiB = newWorkItem("tenant-b-workitem");
        workItemStore.put(wiB);

        // Create note for tenant B
        WorkItemNote noteB = newNote(wiB.id, "note from tenant B");
        store.append(noteB);

        // As tenant A — should only see A's note
        principal.setTenancyId(TENANT_A);
        List<WorkItemNote> resultA = store.findByWorkItemId(wiA.id);
        assertThat(resultA).hasSize(1);
        assertThat(resultA.get(0).id).isEqualTo(noteA.id);

        // As tenant B — should only see B's note
        principal.setTenancyId(TENANT_B);
        List<WorkItemNote> resultB = store.findByWorkItemId(wiB.id);
        assertThat(resultB).hasSize(1);
        assertThat(resultB.get(0).id).isEqualTo(noteB.id);

        // As tenant A trying to see B's notes — should be empty
        principal.setTenancyId(TENANT_A);
        List<WorkItemNote> resultA2 = store.findByWorkItemId(wiB.id);
        assertThat(resultA2).isEmpty();
    }

    // -------------------------------------------------------------------------
    // update() stamps tenancyId when null
    // -------------------------------------------------------------------------

    @Test
    void update_stampsTenancyId_whenNull() {
        // Create work item and note as tenant A
        principal.setTenancyId(TENANT_A);
        WorkItem wi = newWorkItem("test-workitem");
        workItemStore.put(wi);

        WorkItemNote note = newNote(wi.id, "initial content");
        store.append(note);

        // Clear tenancyId (artificial scenario, but tests the guard)
        note.tenancyId = null;
        note.content = "updated content";

        store.update(note);

        assertThat(note.tenancyId).isEqualTo(TENANT_A);
    }

    // -------------------------------------------------------------------------
    // delete() tenant isolation
    // -------------------------------------------------------------------------

    @Test
    void delete_returnsFalse_forAnotherTenantNote() {
        // Create work item and note as tenant A
        principal.setTenancyId(TENANT_A);
        WorkItem wi = newWorkItem("tenant-a-workitem");
        workItemStore.put(wi);

        WorkItemNote note = newNote(wi.id, "tenant-a-note");
        store.append(note);
        UUID noteId = note.id;

        // Switch to tenant B — attempt delete, should fail (not visible)
        principal.setTenancyId(TENANT_B);
        boolean deleted = store.delete(noteId);
        assertThat(deleted).isFalse();

        // Switch back to A — note should still exist
        principal.setTenancyId(TENANT_A);
        Optional<WorkItemNote> found = store.findById(noteId);
        assertThat(found).isPresent();

        // Delete as tenant A — should succeed
        boolean deletedA = store.delete(noteId);
        assertThat(deletedA).isTrue();
        assertThat(store.findById(noteId)).isEmpty();
    }
}
