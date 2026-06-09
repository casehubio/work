package io.casehub.work.runtime.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.casehub.work.runtime.model.WorkItemLink;

/**
 * KV-native store SPI for {@link WorkItemLink} persistence.
 *
 * <p>
 * Provides tenant-scoped CRUD and query operations for external resource links
 * attached to WorkItems. Every query is implicitly scoped to the current tenant
 * via {@code CurrentPrincipal.tenancyId()}.
 *
 * <p>
 * <strong>CDI backend activation (four-tier priority ladder):</strong><br>
 * Tier 1: {@code @ApplicationScoped} (JPA/SQL, default) — {@code casehub-work} runtime.<br>
 * Tier 2: {@code @Alternative @Priority(1)} (MongoDB).<br>
 * Tier 3: {@code @Alternative @Priority(100)} (in-memory, ephemeral).<br>
 * See the platform persistence-backend-cdi-priority protocol.
 */
public interface WorkItemLinkStore {

    /**
     * Persist or update a WorkItemLink and return the saved instance.
     *
     * <p>
     * On insert (entity has no {@code tenancyId}), stamps {@code tenancyId} from
     * {@code CurrentPrincipal.tenancyId()} before persist.
     *
     * @param link the link to persist; must not be {@code null}
     * @return the persisted link
     */
    WorkItemLink put(WorkItemLink link);

    /**
     * Retrieve a WorkItemLink by its primary key, scoped to the current tenant.
     *
     * @param id the UUID primary key
     * @return an {@link Optional} containing the link, or empty if not found or
     *         if the link belongs to a different tenant
     */
    Optional<WorkItemLink> get(UUID id);

    /**
     * All links for a WorkItem, ordered chronologically.
     * Scoped to the current tenant.
     *
     * @param workItemId the WorkItem UUID
     * @return list of links; may be empty, never null
     */
    List<WorkItemLink> findByWorkItemId(UUID workItemId);

    /**
     * Links for a WorkItem filtered to a specific relation type.
     * Scoped to the current tenant.
     *
     * @param workItemId the WorkItem UUID
     * @param type the relation type string
     * @return list of matching links; may be empty, never null
     */
    List<WorkItemLink> findByWorkItemIdAndType(UUID workItemId, String type);

    /**
     * Delete a WorkItemLink by ID, scoped to the current tenant.
     *
     * @param id the UUID primary key
     * @return {@code true} if the link was deleted, {@code false} if not found or
     *         not owned by the current tenant
     */
    boolean delete(UUID id);
}
