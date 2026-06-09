package io.casehub.work.memory;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.work.runtime.model.AuditEntry;
import io.casehub.work.runtime.repository.AuditEntryStore;
import io.casehub.work.runtime.repository.AuditQuery;

/**
 * In-memory implementation of {@link AuditEntryStore} for ephemeral deployments
 * and tests. No datasource or Flyway configuration required.
 *
 * <p>
 * Tier 3 in the CDI priority ladder — {@code @Alternative @Priority(100)} beats
 * both JPA (Tier 1) and MongoDB (Tier 2) when on the classpath.
 *
 * <p>
 * Thread-safe. Data is ephemeral (lost on restart).
 *
 * <p>
 * <strong>Known limitation:</strong> Category filter in {@link AuditQuery} is
 * silently ignored — the filter requires access to the parent WorkItem's category,
 * which would create an inter-store dependency.
 */
@ApplicationScoped
@Alternative
@Priority(100)
public class InMemoryAuditEntryStore implements AuditEntryStore {

    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<AuditEntry>> store = new ConcurrentHashMap<>();

    @Inject
    CurrentPrincipal currentPrincipal;

    /** Removes all stored entries. Available for test isolation ({@code @BeforeEach}) and administrative reset. */
    public void clear() {
        store.clear();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * If {@code entry.id} is {@code null} a fresh {@link UUID} is assigned. If
     * {@code entry.occurredAt} is {@code null} it is set to {@link Instant#now()}.
     * If {@code entry.tenancyId} is {@code null}, stamps it from {@code CurrentPrincipal.tenancyId()}.
     */
    @Override
    public void append(final AuditEntry entry) {
        if (entry.id == null) {
            entry.id = UUID.randomUUID();
        }
        if (entry.occurredAt == null) {
            entry.occurredAt = Instant.now();
        }
        if (entry.tenancyId == null) {
            entry.tenancyId = currentPrincipal.tenancyId();
        }
        store.computeIfAbsent(entry.workItemId, k -> new CopyOnWriteArrayList<>()).add(entry);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Scoped to the current tenant via {@code CurrentPrincipal.tenancyId()}.
     */
    @Override
    public List<AuditEntry> findByWorkItemId(final UUID workItemId) {
        final List<AuditEntry> entries = store.get(workItemId);
        if (entries == null) {
            return List.of();
        }
        final String tenancyId = currentPrincipal.tenancyId();
        return entries.stream()
                .filter(e -> tenancyId.equals(e.tenancyId))
                .sorted(Comparator.comparing(e -> e.occurredAt))
                .toList();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * In-memory implementation for tests — applies actorId, event, from, to filters only.
     * Category filter is not supported (no WorkItem access); it is silently ignored.
     *
     * <p>
     * Scoped to the current tenant via {@code CurrentPrincipal.tenancyId()}.
     */
    @Override
    public List<AuditEntry> query(final AuditQuery query) {
        final String tenancyId = currentPrincipal.tenancyId();
        return store.values().stream()
                .flatMap(List::stream)
                .filter(e -> tenancyId.equals(e.tenancyId))
                .filter(e -> query.actorId() == null || query.actorId().equals(e.actor))
                .filter(e -> query.event() == null || query.event().equals(e.event))
                .filter(e -> query.from() == null || !e.occurredAt.isBefore(query.from()))
                .filter(e -> query.to() == null || !e.occurredAt.isAfter(query.to()))
                .sorted(Comparator.comparing((AuditEntry e) -> e.occurredAt).reversed())
                .skip((long) query.page() * query.size())
                .limit(query.size())
                .toList();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Scoped to the current tenant via {@code CurrentPrincipal.tenancyId()}.
     */
    @Override
    public long count(final AuditQuery query) {
        final String tenancyId = currentPrincipal.tenancyId();
        return store.values().stream()
                .flatMap(List::stream)
                .filter(e -> tenancyId.equals(e.tenancyId))
                .filter(e -> query.actorId() == null || query.actorId().equals(e.actor))
                .filter(e -> query.event() == null || query.event().equals(e.event))
                .filter(e -> query.from() == null || !e.occurredAt.isBefore(query.from()))
                .filter(e -> query.to() == null || !e.occurredAt.isAfter(query.to()))
                .count();
    }
}
