package io.casehub.work.runtime.repository.jpa;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.work.runtime.model.WorkItemSchedule;
import io.casehub.work.runtime.repository.WorkItemScheduleStore;

/**
 * Default JPA/Panache implementation of {@link WorkItemScheduleStore}.
 *
 * <p>
 * Every query is scoped to the current tenant via {@link CurrentPrincipal#tenancyId()}.
 * The {@link #put} method stamps {@code tenancyId} from the principal on insert when
 * the entity does not already carry one.
 */
@ApplicationScoped
public class JpaWorkItemScheduleStore extends TenantAwareStore implements WorkItemScheduleStore {

    @Override
    public WorkItemSchedule put(final WorkItemSchedule schedule) {
        return withTenantQuery(() -> {
            if (schedule.tenancyId == null) {
                schedule.tenancyId = currentPrincipal.tenancyId();
            }
            em.persist(schedule);
            em.flush();
            return schedule;
        });
    }

    @Override
    public Optional<WorkItemSchedule> get(final UUID id) {
        return withTenantQuery(() ->
                em.createQuery("FROM WorkItemSchedule WHERE id = ?1 AND tenancyId = ?2", WorkItemSchedule.class)
                        .setParameter(1, id)
                        .setParameter(2, currentPrincipal.tenancyId())
                        .getResultStream().findFirst());
    }

    @Override
    public List<WorkItemSchedule> scanAll() {
        return withTenantQuery(() ->
                em.createQuery("FROM WorkItemSchedule WHERE tenancyId = ?1 ORDER BY name ASC", WorkItemSchedule.class)
                        .setParameter(1, currentPrincipal.tenancyId())
                        .getResultList());
    }

    @Override
    public boolean delete(final UUID id) {
        return withTenantQuery(() -> {
            final int deleted = em.createQuery("DELETE FROM WorkItemSchedule WHERE id = ?1 AND tenancyId = ?2")
                    .setParameter(1, id)
                    .setParameter(2, currentPrincipal.tenancyId())
                    .executeUpdate();
            return deleted > 0;
        });
    }

    @Override
    public List<WorkItemSchedule> findDue(final Instant now) {
        return withTenantQuery(() ->
                em.createQuery(
                        "FROM WorkItemSchedule WHERE active = true AND nextFireAt IS NOT NULL AND nextFireAt <= ?1 AND tenancyId = ?2 ORDER BY nextFireAt ASC",
                        WorkItemSchedule.class)
                        .setParameter(1, now)
                        .setParameter(2, currentPrincipal.tenancyId())
                        .getResultList());
    }
}
