package io.casehub.work.progress.memory;

import io.casehub.work.progress.ProgressInstance;
import io.casehub.work.progress.spi.ProgressInstanceStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.annotation.Priority;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Alternative
@Priority(100)
@ApplicationScoped
public class InMemoryProgressInstanceStore implements ProgressInstanceStore {

    private final Map<UUID, ProgressInstance> store = new ConcurrentHashMap<>();
    private final Map<UUID, Long> versions = new ConcurrentHashMap<>();

    @Override
    public ProgressInstance put(ProgressInstance instance) {
        UUID id = instance.id();
        Long currentVersion = versions.get(id);

        if (currentVersion == null) {
            versions.put(id, 1L);
            store.put(id, instance);
            return instance;
        }

        store.put(id, instance);
        versions.put(id, currentVersion + 1);
        return instance;
    }

    @Override
    public Optional<ProgressInstance> get(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<ProgressInstance> findByScopeTypeAndScopeId(String scopeType, String scopeId) {
        return store.values().stream()
                .filter(p -> p.scopeType().equals(scopeType) && p.scopeId().equals(scopeId))
                .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
                .toList();
    }

    @Override
    public List<ProgressInstance> findByParentProgressId(UUID parentProgressId) {
        return store.values().stream()
                .filter(p -> parentProgressId.equals(p.parentProgressId()))
                .toList();
    }

    public void clear() {
        store.clear();
        versions.clear();
    }
}
