package io.casehub.work.runtime.repository.jpa;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.work.api.GroupStatus;
import io.casehub.work.api.WorkItem;
import io.casehub.work.api.WorkItemQuery;
import io.casehub.work.api.WorkItemRootView;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.api.WorkItemSummary;
import io.casehub.work.api.WorkItemSummaryBuilder;
import io.casehub.work.api.spi.WorkItemStore;
import io.casehub.work.runtime.model.WorkItemEntity;
import io.casehub.work.runtime.model.WorkItemSpawnGroup;
import io.casehub.work.runtime.repository.WorkItemEntityMapper;
import io.casehub.work.runtime.repository.WorkItemSpawnGroupStore;
import io.casehub.work.runtime.service.SummaryQueryBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Default JPA/Panache implementation of {@link WorkItemStore}.
 *
 * <p>
 * The {@link #scan} method builds a dynamic JPQL query from the non-null fields of
 * the supplied {@link WorkItemQuery}, replacing the five separate query methods of
 * the former {@code JpaWorkItemRepository}.
 *
 * <p>
 * Every query is scoped to the current tenant via {@link CurrentPrincipal#tenancyId()}.
 * The {@link #put} method stamps {@code tenancyId} from the principal on insert when
 * the entity does not already carry one.
 */
@ApplicationScoped
public class JpaWorkItemStore extends TenantAwareStore implements WorkItemStore {

    @Inject
    EntityManager em;

    @Inject
    WorkItemSpawnGroupStore spawnGroupStore;

    @Override
    public WorkItem put(final WorkItem workItem) {
        return withTenantQuery(() -> {
            final WorkItemEntity entity;
            if (workItem.id() != null) {
                final WorkItemEntity existing = em.find(WorkItemEntity.class, workItem.id());
                if (existing != null) {
                    WorkItemEntityMapper.updateEntity(existing, workItem);
                    entity = existing;
                } else {
                    entity = WorkItemEntityMapper.toEntity(workItem);
                }
            } else {
                entity = WorkItemEntityMapper.toEntity(workItem);
            }
            if (entity.tenancyId == null) {
                entity.tenancyId = currentPrincipal.tenancyId();
            }
            entity.persistAndFlush();
            return WorkItemEntityMapper.toDomain(entity);
        });
    }

    @Override
    public Optional<WorkItem> get(final UUID id) {
        return withTenantQuery(() ->
                                       WorkItemEntity.<WorkItemEntity>find("id = ?1 AND tenancyId = ?2", id, currentPrincipal.tenancyId())
                                                     .firstResultOptional()
                                                     .map(WorkItemEntityMapper::toDomain));
    }

    @Override
    public Optional<WorkItem> findByCallerRef(final String callerRef) {
        return withTenantQuery(() ->
                                       WorkItemEntity.<WorkItemEntity>find("callerRef = ?1 AND tenancyId = ?2 ORDER BY createdAt DESC",
                                                                           callerRef, currentPrincipal.tenancyId())
                                                     .firstResultOptional()
                                                     .map(WorkItemEntityMapper::toDomain));
    }

    @Override
    public Optional<WorkItem> findActiveByCallerRef(final String callerRef) {
        return withTenantQuery(() ->
                                       WorkItemEntity.<WorkItemEntity>find("callerRef = ?1 AND status NOT IN (?2) AND tenancyId = ?3 ORDER BY createdAt DESC",
                                                                           callerRef, WorkItemStatus.TERMINAL_STATUSES, currentPrincipal.tenancyId())
                                                     .firstResultOptional()
                                                     .map(WorkItemEntityMapper::toDomain));
    }

    private record JpqlAndParams(String jpql, Map<String, Object> params) {}

    private JpqlAndParams buildScanJpql(final WorkItemQuery query) {
        final Map<String, Object> params = new HashMap<>();
        final StringBuilder jpql = new StringBuilder();

        jpql.append("tenancyId = :tenancyId");
        params.put("tenancyId", query.tenancyId() != null ? query.tenancyId() : currentPrincipal.tenancyId());

        final boolean hasAssigneeId = query.assigneeId() != null;
        final boolean hasCandidateGroups = query.candidateGroups() != null && !query.candidateGroups().isEmpty();
        final boolean hasCandidateUserId = query.candidateUserId() != null;
        final boolean hasAssignment = hasAssigneeId || hasCandidateGroups || hasCandidateUserId;

        if (hasAssignment) {
            jpql.append(" AND (1=0");
            if (hasAssigneeId) {
                jpql.append(" OR assigneeId = :assigneeId OR candidateUsers LIKE :assigneeIdLike");
                params.put("assigneeId", query.assigneeId());
                params.put("assigneeIdLike", "%" + query.assigneeId() + "%");
            }
            if (hasCandidateGroups) {
                for (int i = 0; i < query.candidateGroups().size(); i++) {
                    final String key = "group" + i;
                    jpql.append(" OR candidateGroups LIKE :").append(key);
                    params.put(key, "%" + query.candidateGroups().get(i) + "%");
                }
            }
            if (hasCandidateUserId && !hasAssigneeId) {
                jpql.append(" OR candidateUsers LIKE :candidateUserIdLike");
                params.put("candidateUserIdLike", "%" + query.candidateUserId() + "%");
            }
            jpql.append(")");
        }

        if (query.status() != null) {
            jpql.append(" AND status = :status");
            params.put("status", query.status());
        }
        if (query.statusIn() != null && !query.statusIn().isEmpty()) {
            jpql.append(" AND status IN (:statusIn)");
            params.put("statusIn", query.statusIn());
        }
        if (query.priority() != null) {
            jpql.append(" AND priority = :priority");
            params.put("priority", query.priority());
        }
        if (query.type() != null) {
            jpql.append(" AND id IN (SELECT w.id FROM WorkItemEntity w JOIN w.types t WHERE t.path = :type OR t.path LIKE :typePrefix)");
            params.put("type", query.type());
            params.put("typePrefix", query.type() + "/%");
        }
        if (query.outcome() != null) {
            jpql.append(" AND outcome = :outcome");
            params.put("outcome", query.outcome());
        }
        if (query.followUpBefore() != null) {
            jpql.append(" AND followUpDate <= :followUpBefore");
            params.put("followUpBefore", query.followUpBefore());
        }
        if (query.expiresAtOrBefore() != null) {
            jpql.append(" AND expiresAt <= :expiresAtOrBefore");
            params.put("expiresAtOrBefore", query.expiresAtOrBefore());
        }
        if (query.claimDeadlineOrBefore() != null) {
            jpql.append(" AND claimDeadline <= :claimDeadlineOrBefore");
            params.put("claimDeadlineOrBefore", query.claimDeadlineOrBefore());
        }

        return new JpqlAndParams(jpql.toString(), params);
    }

    @Override
    public List<WorkItem> scan(final WorkItemQuery query) {
        return withTenantQuery(() -> {
            final List<WorkItemEntity> entities;
            if (query.labelPattern() != null) {
                entities = scanByLabelPattern(query.labelPattern());
            } else {
                final JpqlAndParams jp = buildScanJpql(query);
                entities = WorkItemEntity.find(jp.jpql(), jp.params()).list();
            }
            return entities.stream().map(WorkItemEntityMapper::toDomain).toList();
        });
    }

    @Override
    public WorkItemSummary summaryByQuery(final WorkItemQuery query, final Instant now) {
        return withTenantQuery(() -> {
            if (query.labelPattern() != null) {
                return WorkItemSummaryBuilder.build(scan(query), now);
            }
            final JpqlAndParams jp = buildScanJpql(query);
            return SummaryQueryBuilder.build(em, "FROM WorkItemEntity wi WHERE " + jp.jpql(), jp.params(), false, now);
        });
    }

    @Override
    public long countByParentAndAssignee(final UUID parentId, final String assigneeId, final UUID excludeId) {
        return withTenantQuery(() -> {
            // Only count non-terminal instances — terminal children no longer block new claims
            return WorkItemEntity.count(
                    "parentId = ?1 AND assigneeId = ?2 AND id != ?3 AND status NOT IN (?4) AND tenancyId = ?5",
                    parentId, assigneeId, excludeId,
                    WorkItemStatus.TERMINAL_STATUSES,
                    currentPrincipal.tenancyId());
        });
    }

    @Override
    public List<WorkItemRootView> scanRoots(
            final String assignee, final String candidateUser, final List<String> userGroups) {
        return withTenantQuery(() -> {
            // Build visibility predicate using named params (same pattern as scan()).
            // Tenant isolation is always the first predicate.
            final StringBuilder pred = new StringBuilder();
            final Map<String, Object> params = new HashMap<>();

            pred.append("tenancyId = :tenancyId");
            params.put("tenancyId", currentPrincipal.tenancyId());

        // Each non-null dimension is an independent OR predicate, grouped in parens.
        final StringBuilder visibilityPred = new StringBuilder();
        if (assignee != null && !assignee.isBlank()) {
            visibilityPred.append("assigneeId = :assigneeId");
            params.put("assigneeId", assignee);
        }
        if (candidateUser != null && !candidateUser.isBlank()) {
            if (!visibilityPred.isEmpty()) visibilityPred.append(" OR ");
            visibilityPred.append("candidateUsers LIKE :candidateUserLike");
            params.put("candidateUserLike", "%" + candidateUser + "%");
        }
        if (userGroups != null) {
            int gi = 0;
            for (final String group : userGroups) {
                final String key = "grp" + gi++;
                if (!visibilityPred.isEmpty()) {
                    visibilityPred.append(" OR ");
                }
                visibilityPred.append("candidateGroups LIKE :").append(key);
                params.put(key, "%" + group + "%");
            }
        }
        if (visibilityPred.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        pred.append(" AND (").append(visibilityPred).append(")");

            // Find directly visible items
            final List<WorkItemEntity> directlyVisible = WorkItemEntity.find(pred.toString(), params).list();

            // Collect roots (items with parentId IS NULL) including ancestors of visible children
            final LinkedHashSet<UUID>                 rootIds   = new LinkedHashSet<>();
            final LinkedHashMap<UUID, WorkItemEntity> rootItems = new LinkedHashMap<>();
            final String                              tenancyId = currentPrincipal.tenancyId();

            for (final WorkItemEntity item : directlyVisible) {
                if (item.parentId == null) {
                    rootIds.add(item.id);
                    rootItems.put(item.id, item);
                } else {
                    // Tenant-scoped parent lookup (replaces static WorkItem.findById)
                    final WorkItemEntity parent = WorkItemEntity.<WorkItemEntity> find(
                            "id = ?1 AND tenancyId = ?2", item.parentId, tenancyId).firstResult();
                    if (parent != null && parent.parentId == null) {
                        rootIds.add(parent.id);
                        rootItems.put(parent.id, parent);
                    }
                }
            }

            return rootIds.stream().map(id -> {
                final WorkItemEntity     root       = rootItems.get(id);
                final WorkItemSpawnGroup group      = spawnGroupStore.findMultiInstanceByParentId(id).orElse(null);
                final int                childCount = (int) WorkItemEntity.count("parentId = ?1 AND tenancyId = ?2", id, tenancyId);
                if (group != null) {
                    final GroupStatus status = group.groupStatus != null ? group.groupStatus : GroupStatus.IN_PROGRESS;
                    return new WorkItemRootView(WorkItemEntityMapper.toDomain(root), childCount, group.completedCount, group.requiredCount, status);
                }
                return new WorkItemRootView(WorkItemEntityMapper.toDomain(root), childCount, null, null, null);
            }).toList();
        });
    }

    @Override
    public long countByQuery(final WorkItemQuery query) {
        if (query.labelPattern() != null) {
            return countByLabelPattern(query.labelPattern());
        }
        return scan(query).size();
    }

    private long countByLabelPattern(final String pattern) {
        final String tenancyId = currentPrincipal.tenancyId();
        if (pattern.endsWith("/**")) {
            final String prefix = pattern.substring(0, pattern.length() - 3) + "/";
            return WorkItemEntity.count(
                    "SELECT COUNT(DISTINCT wi) FROM WorkItemEntity wi JOIN wi.labels l WHERE wi.tenancyId = ?1 AND l.path LIKE ?2",
                    tenancyId, prefix + "%");
        }
        if (pattern.endsWith("/*")) {
            final String prefix = pattern.substring(0, pattern.length() - 2) + "/";
            return WorkItemEntity.count(
                    "SELECT COUNT(DISTINCT wi) FROM WorkItemEntity wi JOIN wi.labels l " +
                            "WHERE wi.tenancyId = ?1 AND l.path LIKE ?2 AND l.path NOT LIKE ?3",
                    tenancyId, prefix + "%", prefix + "%/%");
        }
        return WorkItemEntity.count(
                "SELECT COUNT(DISTINCT wi) FROM WorkItemEntity wi JOIN wi.labels l WHERE wi.tenancyId = ?1 AND l.path = ?2",
                tenancyId, pattern);
    }

    private List<WorkItemEntity> scanByLabelPattern(final String pattern) {
        final String tenancyId = currentPrincipal.tenancyId();
        if (pattern.endsWith("/**")) {
            final String prefix = pattern.substring(0, pattern.length() - 3) + "/";
            return WorkItemEntity.<WorkItemEntity> find(
                    "SELECT DISTINCT wi FROM WorkItemEntity wi JOIN wi.labels l WHERE wi.tenancyId = ?1 AND l.path LIKE ?2",
                    tenancyId, prefix + "%").list();
        }
        if (pattern.endsWith("/*")) {
            final String prefix = pattern.substring(0, pattern.length() - 2) + "/";
            return WorkItemEntity.<WorkItemEntity> find(
                    "SELECT DISTINCT wi FROM WorkItemEntity wi JOIN wi.labels l " +
                            "WHERE wi.tenancyId = ?1 AND l.path LIKE ?2 AND l.path NOT LIKE ?3",
                    tenancyId, prefix + "%", prefix + "%/%").list();
        }
        return WorkItemEntity.<WorkItemEntity> find(
                "SELECT DISTINCT wi FROM WorkItemEntity wi JOIN wi.labels l WHERE wi.tenancyId = ?1 AND l.path = ?2",
                tenancyId, pattern).list();
    }

    @Override
    public List<WorkItem> findByParentIdExcludingStatuses(final UUID parentId,
                                                          final List<io.casehub.work.api.WorkItemStatus> excludeStatuses) {
        return withTenantQuery(() ->
                                       WorkItemEntity.<WorkItemEntity>find(
                                                             "parentId = ?1 AND tenancyId = ?2 AND status NOT IN (?3)",
                                                             parentId, currentPrincipal.tenancyId(), excludeStatuses)
                                                     .list().stream().map(WorkItemEntityMapper::toDomain).toList());
    }

    @Override
    public List<WorkItem> findByParentIdWithStatuses(final UUID parentId,
                                                     final List<io.casehub.work.api.WorkItemStatus> statuses) {
        return withTenantQuery(() ->
                                       WorkItemEntity.<WorkItemEntity>find(
                                                             "parentId = ?1 AND tenancyId = ?2 AND status IN (?3)",
                                                             parentId, currentPrincipal.tenancyId(), statuses)
                                                     .list().stream().map(WorkItemEntityMapper::toDomain).toList());
    }

    @Override
    public List<WorkItem> findByParentId(final UUID parentId) {
        return withTenantQuery(() ->
                                       WorkItemEntity.<WorkItemEntity>find(
                                                             "parentId = ?1 AND tenancyId = ?2",
                                                             parentId, currentPrincipal.tenancyId())
                                                     .list().stream().map(WorkItemEntityMapper::toDomain).toList());
    }
}
