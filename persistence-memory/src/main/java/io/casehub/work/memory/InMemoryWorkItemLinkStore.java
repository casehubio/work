package io.casehub.work.memory;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.work.runtime.model.WorkItemLink;
import io.casehub.work.runtime.repository.WorkItemLinkStore;

/**
 * In-memory implementation of {@link WorkItemLinkStore} for ephemeral deployments
 * and tests. No datasource or Flyway configuration required.
 *
 * <p>
 * Tier 3 in the CDI priority ladder — {@code @Alternative @Priority(100)} beats
 * both JPA (Tier 1) and MongoDB (Tier 2) when on the classpath.
 *
 * <p>
 * Thread-safe. Data is ephemeral (lost on restart). All operations are tenant-scoped
 * via {@code CurrentPrincipal.tenancyId()}.
 */
@ApplicationScoped
@Alternative
@Priority(100)
public class InMemoryWorkItemLinkStore implements WorkItemLinkStore {

    private final Map<UUID, WorkItemLink> store = new ConcurrentHashMap<>();

    @Inject
    CurrentPrincipal currentPrincipal;

    /** Removes all stored links. Available for test isolation ({@code @BeforeEach}) and administrative reset. */
    public void clear() {
        store.clear();
    }

    @Override
    public WorkItemLink put(final WorkItemLink link) {
        if (link.id == null) {
            link.id = UUID.randomUUID();
        }
        if (link.createdAt == null) {
            link.createdAt = Instant.now();
        }
        if (link.tenancyId == null) {
            link.tenancyId = currentPrincipal.tenancyId();
        }
        store.put(link.id, link);
        return link;
    }

    @Override
    public Optional<WorkItemLink> get(final UUID id) {
        final WorkItemLink link = store.get(id);
        if (link != null && currentPrincipal.tenancyId().equals(link.tenancyId)) {
            return Optional.of(link);
        }
        return Optional.empty();
    }

    @Override
    public List<WorkItemLink> findByWorkItemId(final UUID workItemId) {
        final String tenancyId = currentPrincipal.tenancyId();
        return store.values().stream()
                .filter(l -> tenancyId.equals(l.tenancyId))
                .filter(l -> workItemId.equals(l.workItemId))
                .sorted(Comparator.comparing(l -> l.createdAt))
                .toList();
    }

    @Override
    public List<WorkItemLink> findByWorkItemIdAndType(final UUID workItemId, final String type) {
        final String tenancyId = currentPrincipal.tenancyId();
        return store.values().stream()
                .filter(l -> tenancyId.equals(l.tenancyId))
                .filter(l -> workItemId.equals(l.workItemId) && type.equals(l.relationType))
                .sorted(Comparator.comparing(l -> l.createdAt))
                .toList();
    }

    @Override
    public boolean delete(final UUID id) {
        final WorkItemLink link = store.get(id);
        if (link != null && currentPrincipal.tenancyId().equals(link.tenancyId)) {
            store.remove(id);
            return true;
        }
        return false;
    }
}
