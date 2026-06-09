package io.casehub.work.memory;

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
import io.casehub.work.runtime.model.WorkItemRelation;
import io.casehub.work.runtime.repository.WorkItemRelationStore;

/**
 * In-memory implementation of {@link WorkItemRelationStore} for ephemeral deployments
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
public class InMemoryWorkItemRelationStore implements WorkItemRelationStore {

    private final Map<UUID, WorkItemRelation> store = new ConcurrentHashMap<>();

    @Inject
    CurrentPrincipal currentPrincipal;

    /** Removes all stored relations. Available for test isolation ({@code @BeforeEach}) and administrative reset. */
    public void clear() {
        store.clear();
    }

    @Override
    public WorkItemRelation put(final WorkItemRelation relation) {
        if (relation.id == null) {
            relation.id = UUID.randomUUID();
        }
        if (relation.tenancyId == null) {
            relation.tenancyId = currentPrincipal.tenancyId();
        }
        store.put(relation.id, relation);
        return relation;
    }

    @Override
    public Optional<WorkItemRelation> get(final UUID id) {
        final WorkItemRelation relation = store.get(id);
        if (relation != null && currentPrincipal.tenancyId().equals(relation.tenancyId)) {
            return Optional.of(relation);
        }
        return Optional.empty();
    }

    @Override
    public List<WorkItemRelation> findBySourceId(final UUID sourceId) {
        final String tenancyId = currentPrincipal.tenancyId();
        return store.values().stream()
                .filter(r -> tenancyId.equals(r.tenancyId))
                .filter(r -> sourceId.equals(r.sourceId))
                .sorted(Comparator.comparing(r -> r.createdAt))
                .toList();
    }

    @Override
    public List<WorkItemRelation> findByTargetId(final UUID targetId) {
        final String tenancyId = currentPrincipal.tenancyId();
        return store.values().stream()
                .filter(r -> tenancyId.equals(r.tenancyId))
                .filter(r -> targetId.equals(r.targetId))
                .sorted(Comparator.comparing(r -> r.createdAt))
                .toList();
    }

    @Override
    public List<WorkItemRelation> findBySourceAndType(final UUID sourceId, final String type) {
        final String tenancyId = currentPrincipal.tenancyId();
        return store.values().stream()
                .filter(r -> tenancyId.equals(r.tenancyId))
                .filter(r -> sourceId.equals(r.sourceId) && type.equals(r.relationType))
                .sorted(Comparator.comparing(r -> r.createdAt))
                .toList();
    }

    @Override
    public List<WorkItemRelation> findByTargetAndType(final UUID targetId, final String type) {
        final String tenancyId = currentPrincipal.tenancyId();
        return store.values().stream()
                .filter(r -> tenancyId.equals(r.tenancyId))
                .filter(r -> targetId.equals(r.targetId) && type.equals(r.relationType))
                .sorted(Comparator.comparing(r -> r.createdAt))
                .toList();
    }

    @Override
    public Optional<WorkItemRelation> findExisting(final UUID sourceId, final UUID targetId, final String relationType) {
        final String tenancyId = currentPrincipal.tenancyId();
        return store.values().stream()
                .filter(r -> tenancyId.equals(r.tenancyId))
                .filter(r -> sourceId.equals(r.sourceId)
                        && targetId.equals(r.targetId)
                        && relationType.equals(r.relationType))
                .findFirst();
    }

    @Override
    public boolean delete(final UUID id) {
        final WorkItemRelation relation = store.get(id);
        if (relation != null && currentPrincipal.tenancyId().equals(relation.tenancyId)) {
            store.remove(id);
            return true;
        }
        return false;
    }
}
