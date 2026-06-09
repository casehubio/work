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
import io.casehub.work.runtime.model.LabelVocabulary;
import io.casehub.work.runtime.repository.LabelVocabularyStore;

/**
 * In-memory implementation of {@link LabelVocabularyStore} for ephemeral deployments
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
public class InMemoryLabelVocabularyStore implements LabelVocabularyStore {

    private final Map<UUID, LabelVocabulary> store = new ConcurrentHashMap<>();

    @Inject
    CurrentPrincipal currentPrincipal;

    /** Removes all stored label vocabularies. Available for test isolation ({@code @BeforeEach}) and administrative reset. */
    public void clear() {
        store.clear();
    }

    @Override
    public LabelVocabulary put(final LabelVocabulary vocabulary) {
        if (vocabulary.id == null) {
            vocabulary.id = UUID.randomUUID();
        }
        if (vocabulary.tenancyId == null) {
            vocabulary.tenancyId = currentPrincipal.tenancyId();
        }
        store.put(vocabulary.id, vocabulary);
        return vocabulary;
    }

    @Override
    public Optional<LabelVocabulary> get(final UUID id) {
        final LabelVocabulary vocabulary = store.get(id);
        if (vocabulary != null && currentPrincipal.tenancyId().equals(vocabulary.tenancyId)) {
            return Optional.of(vocabulary);
        }
        return Optional.empty();
    }

    @Override
    public List<LabelVocabulary> scanAll() {
        final String tenancyId = currentPrincipal.tenancyId();
        return store.values().stream()
                .filter(v -> tenancyId.equals(v.tenancyId))
                .toList();
    }

    @Override
    public boolean delete(final UUID id) {
        final LabelVocabulary vocabulary = store.get(id);
        if (vocabulary != null && currentPrincipal.tenancyId().equals(vocabulary.tenancyId)) {
            store.remove(id);
            return true;
        }
        return false;
    }
}
