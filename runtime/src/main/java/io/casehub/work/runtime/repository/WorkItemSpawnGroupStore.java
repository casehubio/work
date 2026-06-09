package io.casehub.work.runtime.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.casehub.work.runtime.model.WorkItemSpawnGroup;

/**
 * KV-native store SPI for {@link WorkItemSpawnGroup} persistence.
 *
 * <p>
 * Provides tenant-scoped CRUD and query operations for spawn group management.
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
public interface WorkItemSpawnGroupStore {

    /**
     * Persist or update a WorkItemSpawnGroup and return the saved instance.
     *
     * <p>
     * On insert (entity has no {@code tenancyId}), stamps {@code tenancyId} from
     * {@code CurrentPrincipal.tenancyId()} before persist.
     *
     * @param group the spawn group to persist; must not be {@code null}
     * @return the persisted spawn group
     */
    WorkItemSpawnGroup put(WorkItemSpawnGroup group);

    /**
     * Retrieve a WorkItemSpawnGroup by its primary key, scoped to the current tenant.
     *
     * @param id the UUID primary key
     * @return an {@link Optional} containing the group, or empty if not found or
     *         if the group belongs to a different tenant
     */
    Optional<WorkItemSpawnGroup> get(UUID id);

    /**
     * All spawn groups for a given parent WorkItem, newest first.
     * Scoped to the current tenant.
     *
     * @param parentId the parent WorkItem UUID
     * @return list of spawn groups; may be empty, never null
     */
    List<WorkItemSpawnGroup> findByParentId(UUID parentId);

    /**
     * Find an existing group by parent + idempotency key.
     * Scoped to the current tenant.
     *
     * @param parentId the parent WorkItem UUID
     * @param groupKey the idempotency key
     * @return an {@link Optional} containing the matching group, or empty if not found
     */
    Optional<WorkItemSpawnGroup> findByParentAndKey(UUID parentId, String groupKey);

    /**
     * Find the multi-instance spawn group for a parent — the group where
     * {@code requiredCount} is set. Returns empty if no multi-instance group exists.
     * Scoped to the current tenant.
     *
     * @param parentId the parent WorkItem UUID
     * @return an {@link Optional} containing the multi-instance group, or empty
     */
    Optional<WorkItemSpawnGroup> findMultiInstanceByParentId(UUID parentId);

    /**
     * Delete a WorkItemSpawnGroup by ID, scoped to the current tenant.
     *
     * @param id the UUID primary key
     * @return {@code true} if the group was deleted, {@code false} if not found or
     *         not owned by the current tenant
     */
    boolean delete(UUID id);
}
