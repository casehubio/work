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
import io.casehub.work.runtime.model.WorkItemTemplate;
import io.casehub.work.runtime.repository.WorkItemTemplateStore;

/**
 * In-memory implementation of {@link WorkItemTemplateStore} for ephemeral deployments
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
public class InMemoryWorkItemTemplateStore implements WorkItemTemplateStore {

    private final Map<UUID, WorkItemTemplate> store = new ConcurrentHashMap<>();

    @Inject
    CurrentPrincipal currentPrincipal;

    /** Removes all stored templates. Available for test isolation ({@code @BeforeEach}) and administrative reset. */
    public void clear() {
        store.clear();
    }

    @Override
    public WorkItemTemplate put(final WorkItemTemplate template) {
        if (template.id == null) {
            template.id = UUID.randomUUID();
        }
        if (template.tenancyId == null) {
            template.tenancyId = currentPrincipal.tenancyId();
        }
        store.put(template.id, template);
        return template;
    }

    @Override
    public Optional<WorkItemTemplate> get(final UUID id) {
        final WorkItemTemplate template = store.get(id);
        if (template != null && currentPrincipal.tenancyId().equals(template.tenancyId)) {
            return Optional.of(template);
        }
        return Optional.empty();
    }

    @Override
    public Optional<WorkItemTemplate> getByName(final String name) {
        final String tenancyId = currentPrincipal.tenancyId();
        return store.values().stream()
                .filter(t -> tenancyId.equals(t.tenancyId))
                .filter(t -> name.equals(t.name))
                .findFirst();
    }

    @Override
    public List<WorkItemTemplate> scanAll() {
        final String tenancyId = currentPrincipal.tenancyId();
        return store.values().stream()
                .filter(t -> tenancyId.equals(t.tenancyId))
                .sorted(Comparator.comparing(t -> t.name))
                .toList();
    }

    @Override
    public boolean delete(final UUID id) {
        final WorkItemTemplate template = store.get(id);
        if (template != null && currentPrincipal.tenancyId().equals(template.tenancyId)) {
            store.remove(id);
            return true;
        }
        return false;
    }
}
