package io.casehub.work.runtime.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemRootView;
import io.casehub.work.api.WorkItemStatus;

/**
 * KV-native store SPI for {@link WorkItem} persistence.
 *
 * <p>
 * Replaces the SQL-shaped {@code WorkItemRepository} with a store interface
 * that separates primary-key operations ({@link #put}, {@link #get}) from
 * query operations ({@link #scan}) using the {@link WorkItemQuery} value object.
 * Backends translate {@code WorkItemQuery} to their native query language.
 *
 * <p>
 * <strong>CDI backend activation (four-tier priority ladder):</strong><br>
 * Tier 0: {@code @DefaultBean} (no-op fallback) — not applicable to this SPI.<br>
 * Tier 1: {@code @ApplicationScoped} (JPA/SQL, default) — {@code casehub-work} runtime.<br>
 * Tier 2: {@code @Alternative @Priority(1)} (MongoDB) — {@code casehub-work-persistence-mongodb}.<br>
 * Tier 3: {@code @Alternative @Priority(100)} (in-memory, ephemeral) — {@code casehub-work-persistence-memory}.<br>
 * Adding a backend module to the classpath activates it automatically — no consumer changes.
 * See the platform
 * <a href="https://github.com/casehubio/garden/blob/main/docs/protocols/universal/persistence-backend-cdi-priority.md">persistence-backend-cdi-priority</a>
 * protocol.
 *
 * @see WorkItemQuery
 */
public interface WorkItemStore {

    /**
     * Persist or update a WorkItem and return the saved instance.
     *
     * <p><strong>Optimistic concurrency control (OCC) contract:</strong>
     * Production implementations <em>must</em> provide OCC on update. Two concurrent
     * {@code put()} calls on the same WorkItem must produce exactly one success and
     * one {@link jakarta.persistence.OptimisticLockException} (or equivalent). The
     * service layer relies on this for claim atomicity — without OCC, two nodes
     * racing to claim the same WorkItem would both succeed, producing a double-claim.
     *
     * <ul>
     *   <li>JPA: {@code @Version} on {@code WorkItem.version} — Hibernate enforces OCC.
     *   <li>MongoDB: version-checked {@code replaceOne} — application-level OCC.
     *   <li>InMemory: no OCC (shared references, {@code ConcurrentHashMap}). Acceptable
     *       because this backend is test-only ({@code @Alternative @Priority(100)}).
     * </ul>
     *
     * @param workItem the work item to persist; must not be {@code null}
     * @return the persisted work item
     * @throws jakarta.persistence.OptimisticLockException if the item was concurrently modified
     */
    WorkItem put(WorkItem workItem);

    /**
     * Retrieve a WorkItem by its primary key.
     *
     * @param id the UUID primary key
     * @return an {@link Optional} containing the work item, or empty if not found
     */
    Optional<WorkItem> get(UUID id);

    /**
     * Scan WorkItems matching the given query criteria.
     *
     * <p>
     * Assignment fields in the query are combined with OR logic; all other fields
     * are combined with AND logic. A {@code null} field imposes no constraint.
     *
     * <p>
     * Use {@link WorkItemQuery} static factories for common patterns:
     * {@link WorkItemQuery#inbox inbox}, {@link WorkItemQuery#expired expired},
     * {@link WorkItemQuery#claimExpired claimExpired},
     * {@link WorkItemQuery#byLabelPattern byLabelPattern}.
     *
     * @param query the query criteria; must not be {@code null}
     * @return list of matching work items; may be empty, never null
     */
    List<WorkItem> scan(WorkItemQuery query);

    /**
     * Return all WorkItems — for admin and monitoring use only.
     * Equivalent to {@code scan(WorkItemQuery.all())}.
     *
     * @return unordered list of all persisted work items
     */
    default List<WorkItem> scanAll() {
        return scan(WorkItemQuery.all());
    }

    /**
     * Count instances in a multi-instance group assigned to the given claimant,
     * excluding the WorkItem being claimed.
     * Returns 0 by default — override in JPA store.
     *
     * @param parentId the UUID of the parent WorkItem whose group is being checked
     * @param assigneeId the claimant to check for existing held instances
     * @param excludeId the WorkItem being claimed (excluded from the count)
     * @return the number of other instances in the group already held by the claimant
     */
    default long countByParentAndAssignee(UUID parentId, String assigneeId, UUID excludeId) {
        return 0L;
    }

    /**
     * Counts WorkItems matching the query without hydrating entities.
     * Default implementation delegates to {@link #scan} — JPA overrides
     * with a native COUNT query for efficiency.
     */
    default long countByQuery(WorkItemQuery query) {
        return scan(query).size();
    }

    /**
     * Find a WorkItem by its caller reference, returning the most recently created match.
     *
     * <p>
     * {@code callerRef} is the opaque string set by the caller when the WorkItem was created
     * (e.g. {@code "case:{caseId}/pi:{planItemId}"}). When multiple WorkItems share the same
     * callerRef, the most recently created is returned.
     *
     * <p>
     * The default implementation performs a linear scan via {@link #scanAll()} — override in
     * JPA/SQL stores for an indexed query.
     *
     * @param callerRef the caller reference to look up; must not be {@code null}
     * @return an {@link Optional} containing the most recently created matching WorkItem, or empty if not found
     */
    default Optional<WorkItem> findByCallerRef(String callerRef) {
        return scanAll().stream()
                .filter(wi -> callerRef.equals(wi.callerRef))
                .max(java.util.Comparator.comparing(wi -> wi.createdAt));
    }

    /**
     * Find a non-terminal (active) WorkItem by its caller reference, returning the most
     * recently created match.
     *
     * <p>
     * {@code callerRef} is the opaque string set by the caller when the WorkItem was created
     * (e.g. {@code "case:{caseId}/pi:{planItemId}"}). Returns only non-terminal WorkItems —
     * useful for idempotent creation checks where a duplicate WorkItem in a terminal status
     * should be ignored.
     *
     * <p>
     * The default implementation performs a linear scan via {@link #scanAll()} — override in
     * JPA/SQL stores for an indexed query on {@code callerRef} with a status filter.
     *
     * @param callerRef the caller reference to look up; must not be {@code null}
     * @return an {@link Optional} containing the most recently created active WorkItem, or empty
     */
    default Optional<WorkItem> findActiveByCallerRef(String callerRef) {
        return scanAll().stream()
                .filter(wi -> callerRef.equals(wi.callerRef))
                .filter(wi -> wi.status != null && wi.status.isActive())
                .max(java.util.Comparator.comparing(wi -> wi.createdAt));
    }

    /**
     * Return root WorkItems (parentId IS NULL) visible to the caller, enriched with aggregate stats.
     *
     * <p>Visibility is an OR across all provided dimensions:
     * <ul>
     *   <li>{@code assignee} — matches {@code assigneeId = assignee}
     *   <li>{@code candidateUser} — matches {@code candidateUsers CONTAINS candidateUser}
     *   <li>{@code candidateGroups} — matches any group in the comma-separated {@code candidateGroups} field
     * </ul>
     *
     * <p>Any null parameter is skipped; if all are null/empty, returns an empty list.
     *
     * @param assignee the worker currently assigned; may be null
     * @param candidateUser a user eligible to claim the WorkItem; may be null
     * @param candidateGroups the groups to check visibility for; may be null or empty
     * @return list of root WorkItems enriched with child stats; never null
     */
    default List<WorkItemRootView> scanRoots(String assignee, String candidateUser, List<String> candidateGroups) {
        return java.util.Collections.emptyList();
    }

    /**
     * Find child WorkItems by parent ID, excluding those in the given terminal statuses.
     * Used by multi-instance group policy for cancelling/suspending remaining children.
     *
     * @param parentId the parent WorkItem UUID
     * @param excludeStatuses statuses to exclude from results
     * @return list of matching child WorkItems; may be empty
     */
    default List<WorkItem> findByParentIdExcludingStatuses(UUID parentId, List<WorkItemStatus> excludeStatuses) {
        return scanAll().stream()
                .filter(wi -> parentId.equals(wi.parentId))
                .filter(wi -> !excludeStatuses.contains(wi.status))
                .toList();
    }

    /**
     * Find child WorkItems by parent ID matching any of the given statuses.
     *
     * @param parentId the parent WorkItem UUID
     * @param statuses statuses to include in results
     * @return list of matching child WorkItems; may be empty
     */
    default List<WorkItem> findByParentIdWithStatuses(UUID parentId, List<WorkItemStatus> statuses) {
        return scanAll().stream()
                .filter(wi -> parentId.equals(wi.parentId))
                .filter(wi -> statuses.contains(wi.status))
                .toList();
    }

    /**
     * Find all child WorkItems by parent ID.
     *
     * @param parentId the parent WorkItem UUID
     * @return list of child WorkItems; may be empty
     */
    default List<WorkItem> findByParentId(UUID parentId) {
        return scanAll().stream()
                .filter(wi -> parentId.equals(wi.parentId))
                .toList();
    }
}
