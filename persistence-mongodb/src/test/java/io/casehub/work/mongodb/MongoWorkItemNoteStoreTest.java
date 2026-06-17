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

import io.casehub.work.runtime.model.WorkItemNote;
import io.casehub.work.runtime.repository.WorkItemNoteStore;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class MongoWorkItemNoteStoreTest {

    @Inject
    WorkItemNoteStore store;

    @Inject
    MutableCurrentPrincipal principal;

    @BeforeEach
    void clearAll() {
        principal.reset();
        MongoWorkItemNoteDocument.deleteAll();
    }

    // ── Append ────────────────────────────────────────────────────────────────

    @Test
    void append_assignsIdAndTimestamps() {
        final WorkItemNote note = note(UUID.randomUUID(), "First note");
        assertThat(note.id).isNull();
        assertThat(note.createdAt).isNull();

        store.append(note);

        assertThat(note.id).isNotNull();
        assertThat(note.createdAt).isNotNull();
    }

    @Test
    void append_and_findById_roundtrip() {
        final UUID workItemId = UUID.randomUUID();
        final WorkItemNote note = note(workItemId, "Roundtrip test");
        note.author = "alice";

        store.append(note);
        final Optional<WorkItemNote> found = store.findById(note.id);

        assertThat(found).isPresent();
        final WorkItemNote loaded = found.get();
        assertThat(loaded.id).isEqualTo(note.id);
        assertThat(loaded.workItemId).isEqualTo(workItemId);
        assertThat(loaded.content).isEqualTo("Roundtrip test");
        assertThat(loaded.author).isEqualTo("alice");
        assertThat(loaded.createdAt).isNotNull();
        assertThat(loaded.editedAt).isNull();
    }

    // ── FindById ──────────────────────────────────────────────────────────────

    @Test
    void findById_returnsEmpty_whenNotFound() {
        assertThat(store.findById(UUID.randomUUID())).isEmpty();
    }

    // ── FindByWorkItemId ──────────────────────────────────────────────────────

    @Test
    void findByWorkItemId_orderedByCreatedAt() {
        final UUID workItemId = UUID.randomUUID();
        final Instant t1 = Instant.now().minus(2, ChronoUnit.HOURS);
        final Instant t2 = Instant.now().minus(1, ChronoUnit.HOURS);
        final Instant t3 = Instant.now();

        final WorkItemNote note1 = note(workItemId, "First");
        note1.createdAt = t1;
        store.append(note1);

        final WorkItemNote note3 = note(workItemId, "Third");
        note3.createdAt = t3;
        store.append(note3);

        final WorkItemNote note2 = note(workItemId, "Second");
        note2.createdAt = t2;
        store.append(note2);

        final List<WorkItemNote> results = store.findByWorkItemId(workItemId);

        assertThat(results).hasSize(3);
        assertThat(results.get(0).content).isEqualTo("First");
        assertThat(results.get(1).content).isEqualTo("Second");
        assertThat(results.get(2).content).isEqualTo("Third");
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Test
    void update_modifiesContent() {
        final WorkItemNote note = note(UUID.randomUUID(), "Original content");
        store.append(note);

        final Instant editTime = Instant.now().plus(1, ChronoUnit.SECONDS).truncatedTo(ChronoUnit.MILLIS);
        note.content = "Updated content";
        note.editedAt = editTime;
        store.update(note);

        final WorkItemNote loaded = store.findById(note.id).orElseThrow();
        assertThat(loaded.content).isEqualTo("Updated content");
        assertThat(loaded.editedAt).isEqualTo(editTime);
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_returnsTrue_whenExists() {
        final WorkItemNote note = note(UUID.randomUUID(), "To delete");
        store.append(note);

        final boolean deleted = store.delete(note.id);

        assertThat(deleted).isTrue();
        assertThat(store.findById(note.id)).isEmpty();
    }

    @Test
    void delete_returnsFalse_whenNotFound() {
        final boolean deleted = store.delete(UUID.randomUUID());

        assertThat(deleted).isFalse();
    }

    // ── Tenant Isolation ──────────────────────────────────────────────────────

    @Test
    void tenantIsolation_noteInvisibleToOtherTenant() {
        final WorkItemNote note = note(UUID.randomUUID(), "Tenant 1 note");
        store.append(note);

        principal.setTenancyId("tenant-2");

        assertThat(store.findById(note.id)).isEmpty();
        assertThat(store.findByWorkItemId(note.workItemId)).isEmpty();
        assertThat(store.delete(note.id)).isFalse();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private WorkItemNote note(final UUID workItemId, final String content) {
        final WorkItemNote note = new WorkItemNote();
        note.workItemId = workItemId;
        note.content = content;
        note.author = "test-user";
        return note;
    }
}
