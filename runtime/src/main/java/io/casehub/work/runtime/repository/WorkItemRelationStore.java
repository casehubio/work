package io.casehub.work.runtime.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.casehub.work.runtime.model.WorkItemRelation;

/**
 * KV-native store SPI for {@link WorkItemRelation} persistence.
 *
 * <p>
 * Provides tenant-scoped CRUD and query operations for the WorkItem relation graph.
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
public interface WorkItemRelationStore {

    /**
     * Persist or update a WorkItemRelation and return the saved instance.
     *
     * <p>
     * On insert (entity has no {@code tenancyId}), stamps {@code tenancyId} from
     * {@code CurrentPrincipal.tenancyId()} before persist.
     *
     * @param relation the relation to persist; must not be {@code null}
     * @return the persisted relation
     */
    WorkItemRelation put(WorkItemRelation relation);

    /**
     * Retrieve a WorkItemRelation by its primary key, scoped to the current tenant.
     *
     * @param id the UUID primary key
     * @return an {@link Optional} containing the relation, or empty if not found or
     *         if the relation belongs to a different tenant
     */
    Optional<WorkItemRelation> get(UUID id);

    /**
     * All outgoing relations from a given WorkItem, ordered by creation time.
     * Scoped to the current tenant.
     *
     * @param sourceId the source WorkItem UUID
     * @return list of outgoing relations; may be empty, never null
     */
    List<WorkItemRelation> findBySourceId(UUID sourceId);

    /**
     * All incoming relations pointing to a given WorkItem, ordered by creation time.
     * Scoped to the current tenant.
     *
     * @param targetId the target WorkItem UUID
     * @return list of incoming relations; may be empty, never null
     */
    List<WorkItemRelation> findByTargetId(UUID targetId);

    /**
     * Outgoing relations of a specific type from a given WorkItem.
     * Scoped to the current tenant.
     *
     * @param sourceId the source WorkItem UUID
     * @param type the relation type string
     * @return list of matching relations; may be empty, never null
     */
    List<WorkItemRelation> findBySourceAndType(UUID sourceId, String type);

    /**
     * Incoming relations of a specific type pointing to a given WorkItem.
     * Scoped to the current tenant.
     *
     * @param targetId the target WorkItem UUID
     * @param type the relation type string
     * @return list of matching relations; may be empty, never null
     */
    List<WorkItemRelation> findByTargetAndType(UUID targetId, String type);

    /**
     * Find an existing relation by source, target, and type — used for duplicate detection.
     * Scoped to the current tenant.
     *
     * @param sourceId the source WorkItem UUID
     * @param targetId the target WorkItem UUID
     * @param relationType the relation type string
     * @return an {@link Optional} containing the matching relation, or empty if not found
     */
    Optional<WorkItemRelation> findExisting(UUID sourceId, UUID targetId, String relationType);

    /**
     * Delete a WorkItemRelation by ID, scoped to the current tenant.
     *
     * @param id the UUID primary key
     * @return {@code true} if the relation was deleted, {@code false} if not found or
     *         not owned by the current tenant
     */
    boolean delete(UUID id);
}
