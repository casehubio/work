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
        store.put(note.id, note);
        return note;
    }

    @Override
    public Optional<WorkItemNote> findById(final UUID noteId) {
        return Optional.ofNullable(store.get(noteId));
    }

    @Override
    public List<WorkItemNote> findByWorkItemId(final UUID workItemId) {
        return store.values().stream()
                .filter(n -> workItemId.equals(n.workItemId))
                .sorted(Comparator.comparing(n -> n.createdAt))
                .toList();
    }

    @Override
    public WorkItemNote update(final WorkItemNote note) {
        store.put(note.id, note);
        return note;
    }

    @Override
    public boolean delete(final UUID noteId) {
        return store.remove(noteId) != null;
    }

    /** Returns all notes, for test inspection and administrative use. */
    public List<WorkItemNote> findAll() {
        return new ArrayList<>(store.values());
    }
}
