package io.casehub.work.runtime.repository.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import io.casehub.work.runtime.model.WorkItemTemplate;
import io.casehub.work.runtime.repository.WorkItemTemplateStore;

/**
 * Default JPA/Panache implementation of {@link WorkItemTemplateStore}.
 *
 * <p>
 * Every query is scoped to the current tenant via {@link CurrentPrincipal#tenancyId()}.
 * The {@link #put} method stamps {@code tenancyId} from the principal on insert when
 * the entity does not already carry one.
 */
@ApplicationScoped
public class JpaWorkItemTemplateStore extends TenantAwareStore implements WorkItemTemplateStore {

    @Override
    public WorkItemTemplate put(final WorkItemTemplate template) {
        return withTenantQuery(() -> {
            if (template.tenancyId == null) {
                template.tenancyId = currentPrincipal.tenancyId();
            }
            em.persist(template);
            em.flush();
            return template;
        });
    }

    @Override
    public Optional<WorkItemTemplate> get(final UUID id) {
        return withTenantQuery(() ->
                em.createQuery("FROM WorkItemTemplate WHERE id = ?1 AND tenancyId = ?2", WorkItemTemplate.class)
                        .setParameter(1, id).setParameter(2, currentPrincipal.tenancyId())
                        .getResultStream().findFirst());
    }

    @Override
    public Optional<WorkItemTemplate> getByName(final String name) {
        return withTenantQuery(() ->
                em.createQuery("FROM WorkItemTemplate WHERE name = ?1 AND tenancyId = ?2", WorkItemTemplate.class)
                        .setParameter(1, name).setParameter(2, currentPrincipal.tenancyId())
                        .getResultStream().findFirst());
    }

    @Override
    public List<WorkItemTemplate> scanAll() {
        return withTenantQuery(() ->
                em.createQuery("FROM WorkItemTemplate WHERE tenancyId = ?1 ORDER BY name ASC", WorkItemTemplate.class)
                        .setParameter(1, currentPrincipal.tenancyId())
                        .getResultList());
    }

    @Override
    public boolean delete(final UUID id) {
        return withTenantQuery(() -> {
            final int deleted = em.createQuery("DELETE FROM WorkItemTemplate WHERE id = ?1 AND tenancyId = ?2")
                    .setParameter(1, id).setParameter(2, currentPrincipal.tenancyId()).executeUpdate();
            return deleted > 0;
        });
    }
}
