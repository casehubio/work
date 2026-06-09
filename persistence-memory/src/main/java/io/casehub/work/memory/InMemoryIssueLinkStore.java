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
import jakarta.inject.Inject;

import io.casehub.platform.api.identity.CurrentPrincipal;
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

    @Inject
    CurrentPrincipal currentPrincipal;

    /** Removes all stored links. Available for test isolation ({@code @BeforeEach}) and administrative reset. */
    public void clear() {
        store.clear();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Scoped to the current tenant via {@code CurrentPrincipal.tenancyId()}.
     */
    @Override
    public Optional<WorkItemIssueLink> findById(final UUID id) {
        final WorkItemIssueLink link = store.get(id);
        if (link != null && currentPrincipal.tenancyId().equals(link.tenancyId)) {
            return Optional.of(link);
        }
        return Optional.empty();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Scoped to the current tenant via {@code CurrentPrincipal.tenancyId()}.
     */
    @Override
    public List<WorkItemIssueLink> findByWorkItemId(final UUID workItemId) {
        final String tenancyId = currentPrincipal.tenancyId();
        return store.values().stream()
                .filter(l -> tenancyId.equals(l.tenancyId))
                .filter(l -> workItemId.equals(l.workItemId))
                .sorted(Comparator.comparing(l -> l.linkedAt))
                .toList();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Scoped to the current tenant via {@code CurrentPrincipal.tenancyId()}.
     */
    @Override
    public Optional<WorkItemIssueLink> findByRef(
            final UUID workItemId, final String trackerType, final String externalRef) {
        final String tenancyId = currentPrincipal.tenancyId();
        return store.values().stream()
                .filter(l -> tenancyId.equals(l.tenancyId))
                .filter(l -> workItemId.equals(l.workItemId)
                        && trackerType.equals(l.trackerType)
                        && externalRef.equals(l.externalRef))
                .findFirst();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Scoped to the current tenant via {@code CurrentPrincipal.tenancyId()}.
     */
    @Override
    public List<WorkItemIssueLink> findByTrackerRef(final String trackerType, final String externalRef) {
        final String tenancyId = currentPrincipal.tenancyId();
        return store.values().stream()
                .filter(l -> tenancyId.equals(l.tenancyId))
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
     * If {@code link.tenancyId} is {@code null}, stamps it from {@code CurrentPrincipal.tenancyId()}.
     */
    @Override
    public WorkItemIssueLink save(final WorkItemIssueLink link) {
        if (link.id == null) {
            link.id = UUID.randomUUID();
        }
        if (link.linkedAt == null) {
            link.linkedAt = Instant.now();
        }
        if (link.tenancyId == null) {
            link.tenancyId = currentPrincipal.tenancyId();
        }
        store.put(link.id, link);
        return link;
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Scoped to the current tenant via {@code CurrentPrincipal.tenancyId()}.
     */
    @Override
    public void delete(final WorkItemIssueLink link) {
        if (link != null && currentPrincipal.tenancyId().equals(link.tenancyId)) {
            store.remove(link.id);
        }
    }
}
