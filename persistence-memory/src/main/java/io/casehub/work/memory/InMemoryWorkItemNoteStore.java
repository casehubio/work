package io.casehub.work.memory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.work.runtime.model.WorkItemNote;
import io.casehub.work.runtime.repository.WorkItemNoteStore;

/**
 * In-memory implementation of {@link WorkItemNoteStore} for ephemeral deployments
 * and tests. No datasource or Flyway configuration required.
 *
 * <p>
 * Tier 3 in the CDI priority ladder — {@code @Alternative @Priority(100)} beats
 * JPA (Tier 1) when on the classpath. No Tier 2 (MongoDB) exists for this SPI
 * yet (tracked as casehubio/work#253).
 *
 * <p>
 * Thread-safe. Data is ephemeral (lost on restart).
 */
@ApplicationScoped
@Alternative
@Priority(100)
public class InMemoryWorkItemNoteStore implements WorkItemNoteStore {

    private final Map<UUID, WorkItemNote> store = new ConcurrentHashMap<>();

    @Inject
    CurrentPrincipal currentPrincipal;

    /** Removes all stored notes. Available for test isolation ({@code @BeforeEach}) and administrative reset. */
    public void clear() {
        store.clear();
    }

    @Override
    public WorkItemNote append(final WorkItemNote note) {
        if (note.id == null) {
            note.id = UUID.randomUUID();
        }
        if (note.createdAt == null) {
            note.createdAt = Instant.now();
        }
        if (note.tenancyId == null) {
            note.tenancyId = currentPrincipal.tenancyId();
        }
        store.put(note.id, note);
        return note;
    }

    @Override
    public Optional<WorkItemNote> findById(final UUID noteId) {
        final WorkItemNote note = store.get(noteId);
        if (note != null && currentPrincipal.tenancyId().equals(note.tenancyId)) {
            return Optional.of(note);
        }
        return Optional.empty();
    }

    @Override
    public List<WorkItemNote> findByWorkItemId(final UUID workItemId) {
        final String tenancyId = currentPrincipal.tenancyId();
        return store.values().stream()
                .filter(n -> tenancyId.equals(n.tenancyId))
                .filter(n -> workItemId.equals(n.workItemId))
                .sorted(Comparator.comparing(n -> n.createdAt))
                .toList();
    }

    @Override
    public WorkItemNote update(final WorkItemNote note) {
        // Preserve tenancyId on update
        if (note.tenancyId == null) {
            note.tenancyId = currentPrincipal.tenancyId();
        }
        store.put(note.id, note);
        return note;
    }

    @Override
    public boolean delete(final UUID noteId) {
        final WorkItemNote note = store.get(noteId);
        if (note != null && currentPrincipal.tenancyId().equals(note.tenancyId)) {
            store.remove(noteId);
            return true;
        }
        return false;
    }

    /** Returns all notes, for test inspection and administrative use. */
    public List<WorkItemNote> findAll() {
        return new ArrayList<>(store.values());
    }
}
