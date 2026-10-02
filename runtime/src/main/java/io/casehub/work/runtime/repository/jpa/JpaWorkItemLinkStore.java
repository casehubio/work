package io.casehub.work.runtime.repository.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import io.casehub.work.runtime.model.WorkItemLink;
import io.casehub.work.runtime.repository.WorkItemLinkStore;

/**
 * Default JPA/Panache implementation of {@link WorkItemLinkStore}.
 *
 * <p>
 * Every query is scoped to the current tenant via {@link CurrentPrincipal#tenancyId()}.
 * The {@link #put} method stamps {@code tenancyId} from the principal on insert when
 * the entity does not already carry one.
 */
@ApplicationScoped
public class JpaWorkItemLinkStore extends TenantAwareStore implements WorkItemLinkStore {

    @Override
    public WorkItemLink put(final WorkItemLink link) {
        return withTenantQuery(() -> {
            if (link.tenancyId == null) {
                link.tenancyId = currentPrincipal.tenancyId();
            }
            em.persist(link);
            em.flush();
            return link;
        });
    }

    @Override
    public Optional<WorkItemLink> get(final UUID id) {
        return withTenantQuery(() ->
                em.createQuery("FROM WorkItemLink WHERE id = ?1 AND tenancyId = ?2", WorkItemLink.class)
                        .setParameter(1, id).setParameter(2, currentPrincipal.tenancyId())
                        .getResultStream().findFirst());
    }

    @Override
    public List<WorkItemLink> findByWorkItemId(final UUID workItemId) {
        return withTenantQuery(() ->
                em.createQuery("FROM WorkItemLink WHERE workItemId = ?1 AND tenancyId = ?2 ORDER BY createdAt ASC", WorkItemLink.class)
                        .setParameter(1, workItemId).setParameter(2, currentPrincipal.tenancyId())
                        .getResultList());
    }

    @Override
    public List<WorkItemLink> findByWorkItemIdAndType(final UUID workItemId, final String type) {
        return withTenantQuery(() ->
                em.createQuery("FROM WorkItemLink WHERE workItemId = ?1 AND relationType = ?2 AND tenancyId = ?3 ORDER BY createdAt ASC", WorkItemLink.class)
                        .setParameter(1, workItemId).setParameter(2, type).setParameter(3, currentPrincipal.tenancyId())
                        .getResultList());
    }

    @Override
    public boolean delete(final UUID id) {
        return withTenantQuery(() -> {
            final int deleted = em.createQuery("DELETE FROM WorkItemLink WHERE id = ?1 AND tenancyId = ?2")
                    .setParameter(1, id).setParameter(2, currentPrincipal.tenancyId()).executeUpdate();
            return deleted > 0;
        });
    }
}
