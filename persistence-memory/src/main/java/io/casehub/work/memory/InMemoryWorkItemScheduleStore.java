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
import io.casehub.work.runtime.model.WorkItemSchedule;
import io.casehub.work.runtime.repository.WorkItemScheduleStore;

/**
 * In-memory implementation of {@link WorkItemScheduleStore} for ephemeral deployments
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
public class InMemoryWorkItemScheduleStore implements WorkItemScheduleStore {

    private final Map<UUID, WorkItemSchedule> store = new ConcurrentHashMap<>();

    @Inject
    CurrentPrincipal currentPrincipal;

    /** Removes all stored schedules. Available for test isolation ({@code @BeforeEach}) and administrative reset. */
    public void clear() {
        store.clear();
    }

    @Override
    public WorkItemSchedule put(final WorkItemSchedule schedule) {
        if (schedule.id == null) {
            schedule.id = UUID.randomUUID();
        }
        if (schedule.tenancyId == null) {
            schedule.tenancyId = currentPrincipal.tenancyId();
        }
        store.put(schedule.id, schedule);
        return schedule;
    }

    @Override
    public Optional<WorkItemSchedule> get(final UUID id) {
        final WorkItemSchedule schedule = store.get(id);
        if (schedule != null && currentPrincipal.tenancyId().equals(schedule.tenancyId)) {
            return Optional.of(schedule);
        }
        return Optional.empty();
    }

    @Override
    public List<WorkItemSchedule> scanAll() {
        final String tenancyId = currentPrincipal.tenancyId();
        return store.values().stream()
                .filter(s -> tenancyId.equals(s.tenancyId))
                .sorted(Comparator.comparing(s -> s.name))
                .toList();
    }

    @Override
    public boolean delete(final UUID id) {
        final WorkItemSchedule schedule = store.get(id);
        if (schedule != null && currentPrincipal.tenancyId().equals(schedule.tenancyId)) {
            store.remove(id);
            return true;
        }
        return false;
    }

    @Override
    public List<WorkItemSchedule> findDue(final Instant now) {
        final String tenancyId = currentPrincipal.tenancyId();
        return store.values().stream()
                .filter(s -> tenancyId.equals(s.tenancyId))
                .filter(s -> s.active && s.nextFireAt != null && !s.nextFireAt.isAfter(now))
                .sorted(Comparator.comparing(s -> s.nextFireAt))
                .toList();
    }
}
