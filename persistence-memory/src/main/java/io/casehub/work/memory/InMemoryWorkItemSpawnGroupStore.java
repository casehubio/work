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
import io.casehub.work.runtime.model.WorkItemSpawnGroup;
import io.casehub.work.runtime.repository.WorkItemSpawnGroupStore;

/**
 * In-memory implementation of {@link WorkItemSpawnGroupStore} for ephemeral deployments
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
public class InMemoryWorkItemSpawnGroupStore implements WorkItemSpawnGroupStore {

    private final Map<UUID, WorkItemSpawnGroup> store = new ConcurrentHashMap<>();

    @Inject
    CurrentPrincipal currentPrincipal;

    /** Removes all stored spawn groups. Available for test isolation ({@code @BeforeEach}) and administrative reset. */
    public void clear() {
        store.clear();
    }

    @Override
    public WorkItemSpawnGroup put(final WorkItemSpawnGroup group) {
        if (group.id == null) {
            group.id = UUID.randomUUID();
        }
        if (group.createdAt == null) {
            group.createdAt = Instant.now();
        }
        if (group.tenancyId == null) {
            group.tenancyId = currentPrincipal.tenancyId();
        }
        store.put(group.id, group);
        return group;
    }

    @Override
    public Optional<WorkItemSpawnGroup> get(final UUID id) {
        final WorkItemSpawnGroup group = store.get(id);
        if (group != null && currentPrincipal.tenancyId().equals(group.tenancyId)) {
            return Optional.of(group);
        }
        return Optional.empty();
    }

    @Override
    public List<WorkItemSpawnGroup> findByParentId(final UUID parentId) {
        final String tenancyId = currentPrincipal.tenancyId();
        return store.values().stream()
                .filter(g -> tenancyId.equals(g.tenancyId))
                .filter(g -> parentId.equals(g.parentId))
                .sorted(Comparator.comparing((WorkItemSpawnGroup g) -> g.createdAt).reversed())
                .toList();
    }

    @Override
    public Optional<WorkItemSpawnGroup> findByParentAndKey(final UUID parentId, final String groupKey) {
        final String tenancyId = currentPrincipal.tenancyId();
        return store.values().stream()
                .filter(g -> tenancyId.equals(g.tenancyId))
                .filter(g -> parentId.equals(g.parentId) && groupKey.equals(g.idempotencyKey))
                .findFirst();
    }

    @Override
    public Optional<WorkItemSpawnGroup> findMultiInstanceByParentId(final UUID parentId) {
        final String tenancyId = currentPrincipal.tenancyId();
        return store.values().stream()
                .filter(g -> tenancyId.equals(g.tenancyId))
                .filter(g -> parentId.equals(g.parentId))
                .filter(g -> g.requiredCount != null && g.requiredCount > 0)
                .findFirst();
    }

    @Override
    public boolean delete(final UUID id) {
        final WorkItemSpawnGroup group = store.get(id);
        if (group != null && currentPrincipal.tenancyId().equals(group.tenancyId)) {
            store.remove(id);
            return true;
        }
        return false;
    }
}
