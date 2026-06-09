package io.casehub.work.memory;

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
import io.casehub.platform.api.path.Path;
import io.casehub.work.runtime.model.LabelDefinition;
import io.casehub.work.runtime.repository.LabelDefinitionStore;

/**
 * In-memory implementation of {@link LabelDefinitionStore} for ephemeral deployments
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
public class InMemoryLabelDefinitionStore implements LabelDefinitionStore {

    private final Map<UUID, LabelDefinition> store = new ConcurrentHashMap<>();

    @Inject
    CurrentPrincipal currentPrincipal;

    /** Removes all stored label definitions. Available for test isolation ({@code @BeforeEach}) and administrative reset. */
    public void clear() {
        store.clear();
    }

    @Override
    public LabelDefinition put(final LabelDefinition definition) {
        if (definition.id == null) {
            definition.id = UUID.randomUUID();
        }
        if (definition.tenancyId == null) {
            definition.tenancyId = currentPrincipal.tenancyId();
        }
        store.put(definition.id, definition);
        return definition;
    }

    @Override
    public Optional<LabelDefinition> get(final UUID id) {
        final LabelDefinition definition = store.get(id);
        if (definition != null && currentPrincipal.tenancyId().equals(definition.tenancyId)) {
            return Optional.of(definition);
        }
        return Optional.empty();
    }

    @Override
    public List<LabelDefinition> findByVocabularyId(final UUID vocabularyId) {
        final String tenancyId = currentPrincipal.tenancyId();
        return store.values().stream()
                .filter(d -> tenancyId.equals(d.tenancyId))
                .filter(d -> vocabularyId.equals(d.vocabularyId))
                .toList();
    }

    @Override
    public List<LabelDefinition> findByPath(final Path path) {
        final String tenancyId = currentPrincipal.tenancyId();
        return store.values().stream()
                .filter(d -> tenancyId.equals(d.tenancyId))
                .filter(d -> path.equals(d.path))
                .toList();
    }

    @Override
    public boolean delete(final UUID id) {
        final LabelDefinition definition = store.get(id);
        if (definition != null && currentPrincipal.tenancyId().equals(definition.tenancyId)) {
            store.remove(id);
            return true;
        }
        return false;
    }
}
