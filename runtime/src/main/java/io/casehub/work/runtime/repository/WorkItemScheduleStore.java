package io.casehub.work.runtime.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.casehub.work.runtime.model.WorkItemSchedule;

/**
 * KV-native store SPI for {@link WorkItemSchedule} persistence.
 *
 * <p>
 * Provides tenant-scoped CRUD and query operations for schedule management.
 * Every query is implicitly scoped to the current tenant via
 * {@code CurrentPrincipal.tenancyId()}.
 *
 * <p>
 * <strong>CDI backend activation (four-tier priority ladder):</strong><br>
 * Tier 1: {@code @ApplicationScoped} (JPA/SQL, default) — {@code casehub-work} runtime.<br>
 * Tier 2: {@code @Alternative @Priority(1)} (MongoDB).<br>
 * Tier 3: {@code @Alternative @Priority(100)} (in-memory, ephemeral).<br>
 * See the platform persistence-backend-cdi-priority protocol.
 */
public interface WorkItemScheduleStore {

    /**
     * Persist or update a WorkItemSchedule and return the saved instance.
     *
     * <p>
     * On insert (entity has no {@code tenancyId}), stamps {@code tenancyId} from
     * {@code CurrentPrincipal.tenancyId()} before persist.
     *
     * @param schedule the schedule to persist; must not be {@code null}
     * @return the persisted schedule
     */
    WorkItemSchedule put(WorkItemSchedule schedule);

    /**
     * Retrieve a WorkItemSchedule by its primary key, scoped to the current tenant.
     *
     * @param id the UUID primary key
     * @return an {@link Optional} containing the schedule, or empty if not found or
     *         if the schedule belongs to a different tenant
     */
    Optional<WorkItemSchedule> get(UUID id);

    /**
     * Return all schedules visible to the current tenant, ordered by name ascending.
     *
     * @return list of schedules; may be empty, never null
     */
    List<WorkItemSchedule> scanAll();

    /**
     * Delete a WorkItemSchedule by ID, scoped to the current tenant.
     *
     * @param id the UUID primary key
     * @return {@code true} if the schedule was deleted, {@code false} if not found or
     *         not owned by the current tenant
     */
    boolean delete(UUID id);

    /**
     * All active schedules whose {@code nextFireAt} is on or before {@code now},
     * scoped to the current tenant.
     *
     * @param now the reference instant
     * @return list of due schedules; may be empty, never null
     */
    List<WorkItemSchedule> findDue(Instant now);
}
