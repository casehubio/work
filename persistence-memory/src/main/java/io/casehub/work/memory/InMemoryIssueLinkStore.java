package io.casehub.work.memory;

import java.time.Instant;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import io.casehub.work.issuetracker.model.WorkItemIssueLink;
import io.casehub.work.issuetracker.repository.IssueLinkStore;

/**
 * In-memory implementation of {@link IssueLinkStore} for ephemeral deployments
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
public class InMemoryIssueLinkStore implements IssueLinkStore {

    private final Map<UUID, WorkItemIssueLink> store = new ConcurrentHashMap<>();

    /** Removes all stored links. Available for test isolation ({@code @BeforeEach}) and administrative reset. */
    public void clear() {
        store.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<WorkItemIssueLink> findById(final UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<WorkItemIssueLink> findByWorkItemId(final UUID workItemId) {
        return store.values().stream()
                .filter(l -> workItemId.equals(l.workItemId))
                .sorted(Comparator.comparing(l -> l.linkedAt))
                .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<WorkItemIssueLink> findByRef(
            final UUID workItemId, final String trackerType, final String externalRef) {
        return store.values().stream()
                .filter(l -> workItemId.equals(l.workItemId)
                        && trackerType.equals(l.trackerType)
                        && externalRef.equals(l.externalRef))
                .findFirst();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<WorkItemIssueLink> findByTrackerRef(final String trackerType, final String externalRef) {
        return store.values().stream()
                .filter(l -> trackerType.equals(l.trackerType) && externalRef.equals(l.externalRef))
                .toList();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * If {@code link.id} is {@code null}, a fresh {@link UUID} is assigned
     * (replicating what {@code @PrePersist} does in the JPA implementation).
     * If {@code link.linkedAt} is {@code null}, it is set to {@link Instant#now()}.
     */
    @Override
    public WorkItemIssueLink save(final WorkItemIssueLink link) {
        if (link.id == null) {
            link.id = UUID.randomUUID();
        }
        if (link.linkedAt == null) {
            link.linkedAt = Instant.now();
        }
        store.put(link.id, link);
        return link;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(final WorkItemIssueLink link) {
        store.remove(link.id);
    }
}
