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
import io.casehub.work.runtime.filter.FilterRule;
import io.casehub.work.runtime.repository.FilterRuleStore;

/**
 * In-memory implementation of {@link FilterRuleStore} for ephemeral deployments
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
public class InMemoryFilterRuleStore implements FilterRuleStore {

    private final Map<UUID, FilterRule> store = new ConcurrentHashMap<>();

    @Inject
    CurrentPrincipal currentPrincipal;

    /** Removes all stored filter rules. Available for test isolation ({@code @BeforeEach}) and administrative reset. */
    public void clear() {
        store.clear();
    }

    @Override
    public FilterRule put(final FilterRule rule) {
        if (rule.id == null) {
            rule.id = UUID.randomUUID();
        }
        if (rule.tenancyId == null) {
            rule.tenancyId = currentPrincipal.tenancyId();
        }
        store.put(rule.id, rule);
        return rule;
    }

    @Override
    public Optional<FilterRule> get(final UUID id) {
        final FilterRule rule = store.get(id);
        if (rule != null && currentPrincipal.tenancyId().equals(rule.tenancyId)) {
            return Optional.of(rule);
        }
        return Optional.empty();
    }

    @Override
    public List<FilterRule> allEnabled() {
        final String tenancyId = currentPrincipal.tenancyId();
        return store.values().stream()
                .filter(r -> tenancyId.equals(r.tenancyId))
                .filter(r -> r.enabled)
                .sorted(Comparator.comparing(r -> r.createdAt))
                .toList();
    }

    @Override
    public List<FilterRule> scanAll() {
        final String tenancyId = currentPrincipal.tenancyId();
        return store.values().stream()
                .filter(r -> tenancyId.equals(r.tenancyId))
                .sorted(Comparator.comparing(r -> r.createdAt))
                .toList();
    }

    @Override
    public boolean delete(final UUID id) {
        final FilterRule rule = store.get(id);
        if (rule != null && currentPrincipal.tenancyId().equals(rule.tenancyId)) {
            store.remove(id);
            return true;
        }
        return false;
    }
}
