package io.casehub.work.runtime.repository.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import io.casehub.work.runtime.model.WorkItemSpawnGroup;
import io.casehub.work.runtime.repository.WorkItemSpawnGroupStore;

/**
 * Default JPA/Panache implementation of {@link WorkItemSpawnGroupStore}.
 *
 * <p>
 * Every query is scoped to the current tenant via {@link CurrentPrincipal#tenancyId()}.
 * The {@link #put} method stamps {@code tenancyId} from the principal on insert when
 * the entity does not already carry one.
 */
@ApplicationScoped
public class JpaWorkItemSpawnGroupStore extends TenantAwareStore implements WorkItemSpawnGroupStore {

    @Override
    public WorkItemSpawnGroup put(final WorkItemSpawnGroup group) {
        return withTenantQuery(() -> {
            if (group.tenancyId == null) {
                group.tenancyId = currentPrincipal.tenancyId();
            }
            em.persist(group);
            em.flush();
            return group;
        });
    }

    @Override
    public Optional<WorkItemSpawnGroup> get(final UUID id) {
        return withTenantQuery(() ->
                em.createQuery("FROM WorkItemSpawnGroup WHERE id = ?1 AND tenancyId = ?2", WorkItemSpawnGroup.class)
                        .setParameter(1, id).setParameter(2, currentPrincipal.tenancyId())
                        .getResultStream().findFirst());
    }

    @Override
    public List<WorkItemSpawnGroup> findByParentId(final UUID parentId) {
        return withTenantQuery(() ->
                em.createQuery("FROM WorkItemSpawnGroup WHERE parentId = ?1 AND tenancyId = ?2 ORDER BY createdAt DESC", WorkItemSpawnGroup.class)
                        .setParameter(1, parentId).setParameter(2, currentPrincipal.tenancyId())
                        .getResultList());
    }

    @Override
    public Optional<WorkItemSpawnGroup> findByParentAndKey(final UUID parentId, final String groupKey) {
        return withTenantQuery(() ->
                em.createQuery("FROM WorkItemSpawnGroup WHERE parentId = ?1 AND idempotencyKey = ?2 AND tenancyId = ?3", WorkItemSpawnGroup.class)
                        .setParameter(1, parentId).setParameter(2, groupKey).setParameter(3, currentPrincipal.tenancyId())
                        .getResultStream().findFirst());
    }

    @Override
    public Optional<WorkItemSpawnGroup> findMultiInstanceByParentId(final UUID parentId) {
        return withTenantQuery(() ->
                em.createQuery("FROM WorkItemSpawnGroup WHERE parentId = ?1 AND requiredCount IS NOT NULL AND tenancyId = ?2", WorkItemSpawnGroup.class)
                        .setParameter(1, parentId).setParameter(2, currentPrincipal.tenancyId())
                        .getResultStream().findFirst());
    }

    @Override
    public Optional<WorkItemSpawnGroup> findMultiInstanceByParentIdForUpdate(final UUID parentId) {
        return withTenantQuery(() ->
                em.createQuery("FROM WorkItemSpawnGroup WHERE parentId = ?1 AND requiredCount IS NOT NULL AND tenancyId = ?2", WorkItemSpawnGroup.class)
                        .setParameter(1, parentId).setParameter(2, currentPrincipal.tenancyId())
                        .setLockMode(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
                        .getResultStream().findFirst());
    }

    @Override
    public boolean delete(final UUID id) {
        return withTenantQuery(() -> {
            final int deleted = em.createQuery("DELETE FROM WorkItemSpawnGroup WHERE id = ?1 AND tenancyId = ?2")
                    .setParameter(1, id).setParameter(2, currentPrincipal.tenancyId()).executeUpdate();
            return deleted > 0;
        });
    }
}
