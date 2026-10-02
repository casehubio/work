package io.casehub.work.runtime.repository.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import io.casehub.work.runtime.model.WorkItemRelation;
import io.casehub.work.runtime.repository.WorkItemRelationStore;

/**
 * Default JPA/Panache implementation of {@link WorkItemRelationStore}.
 *
 * <p>
 * Every query is scoped to the current tenant via {@link CurrentPrincipal#tenancyId()}.
 * The {@link #put} method stamps {@code tenancyId} from the principal on insert when
 * the entity does not already carry one.
 */
@ApplicationScoped
public class JpaWorkItemRelationStore extends TenantAwareStore implements WorkItemRelationStore {

    @Override
    public WorkItemRelation put(final WorkItemRelation relation) {
        return withTenantQuery(() -> {
            if (relation.tenancyId == null) {
                relation.tenancyId = currentPrincipal.tenancyId();
            }
            em.persist(relation);
            em.flush();
            return relation;
        });
    }

    @Override
    public Optional<WorkItemRelation> get(final UUID id) {
        return withTenantQuery(() ->
                em.createQuery("FROM WorkItemRelation WHERE id = ?1 AND tenancyId = ?2", WorkItemRelation.class)
                        .setParameter(1, id).setParameter(2, currentPrincipal.tenancyId())
                        .getResultStream().findFirst());
    }

    @Override
    public List<WorkItemRelation> findBySourceId(final UUID sourceId) {
        return withTenantQuery(() ->
                em.createQuery("FROM WorkItemRelation WHERE sourceId = ?1 AND tenancyId = ?2 ORDER BY createdAt ASC", WorkItemRelation.class)
                        .setParameter(1, sourceId).setParameter(2, currentPrincipal.tenancyId())
                        .getResultList());
    }

    @Override
    public List<WorkItemRelation> findByTargetId(final UUID targetId) {
        return withTenantQuery(() ->
                em.createQuery("FROM WorkItemRelation WHERE targetId = ?1 AND tenancyId = ?2 ORDER BY createdAt ASC", WorkItemRelation.class)
                        .setParameter(1, targetId).setParameter(2, currentPrincipal.tenancyId())
                        .getResultList());
    }

    @Override
    public List<WorkItemRelation> findBySourceAndType(final UUID sourceId, final String type) {
        return withTenantQuery(() ->
                em.createQuery("FROM WorkItemRelation WHERE sourceId = ?1 AND relationType = ?2 AND tenancyId = ?3 ORDER BY createdAt ASC", WorkItemRelation.class)
                        .setParameter(1, sourceId).setParameter(2, type).setParameter(3, currentPrincipal.tenancyId())
                        .getResultList());
    }

    @Override
    public List<WorkItemRelation> findByTargetAndType(final UUID targetId, final String type) {
        return withTenantQuery(() ->
                em.createQuery("FROM WorkItemRelation WHERE targetId = ?1 AND relationType = ?2 AND tenancyId = ?3 ORDER BY createdAt ASC", WorkItemRelation.class)
                        .setParameter(1, targetId).setParameter(2, type).setParameter(3, currentPrincipal.tenancyId())
                        .getResultList());
    }

    @Override
    public Optional<WorkItemRelation> findExisting(final UUID sourceId, final UUID targetId,
            final String relationType) {
        return withTenantQuery(() ->
                em.createQuery("FROM WorkItemRelation WHERE sourceId = ?1 AND targetId = ?2 AND relationType = ?3 AND tenancyId = ?4", WorkItemRelation.class)
                        .setParameter(1, sourceId).setParameter(2, targetId).setParameter(3, relationType).setParameter(4, currentPrincipal.tenancyId())
                        .getResultStream().findFirst());
    }

    @Override
    public boolean delete(final UUID id) {
        return withTenantQuery(() -> {
            final int deleted = em.createQuery("DELETE FROM WorkItemRelation WHERE id = ?1 AND tenancyId = ?2")
                    .setParameter(1, id).setParameter(2, currentPrincipal.tenancyId()).executeUpdate();
            return deleted > 0;
        });
    }
}
