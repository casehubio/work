package io.casehub.work.ai.repository.jpa;

import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.work.ai.repository.WorkerSkillProfileStore;
import io.casehub.work.ai.skill.WorkerSkillProfile;
import io.casehub.work.runtime.repository.jpa.TenantAwareStore;

/**
 * Default JPA implementation of {@link WorkerSkillProfileStore}.
 *
 * <p>Every query is scoped to the current tenant via {@link CurrentPrincipal#tenancyId()}.
 * The {@link #put} method stamps {@code tenancyId} from the principal on insert when
 * the entity does not already carry one.
 */
@ApplicationScoped
public class JpaWorkerSkillProfileStore extends TenantAwareStore implements WorkerSkillProfileStore {

    @Override
    public WorkerSkillProfile put(final WorkerSkillProfile profile) {
        return withTenantQuery(() -> {
            if (profile.tenancyId == null) {
                profile.tenancyId = currentPrincipal.tenancyId();
            }
            em.persist(profile);
            em.flush();
            return profile;
        });
    }

    @Override
    public Optional<WorkerSkillProfile> get(final String workerId) {
        return withTenantQuery(() ->
            em.createQuery("FROM WorkerSkillProfile WHERE workerId = ?1 AND tenancyId = ?2", WorkerSkillProfile.class)
                    .setParameter(1, workerId).setParameter(2, currentPrincipal.tenancyId())
                    .getResultStream().findFirst()
        );
    }

    @Override
    public List<WorkerSkillProfile> scanAll() {
        return withTenantQuery(() ->
            em.createQuery("FROM WorkerSkillProfile WHERE tenancyId = ?1", WorkerSkillProfile.class)
                    .setParameter(1, currentPrincipal.tenancyId()).getResultList()
        );
    }

    @Override
    public boolean delete(final String workerId) {
        return withTenantQuery(() -> {
            final long deleted = em.createQuery("DELETE FROM WorkerSkillProfile WHERE workerId = ?1 AND tenancyId = ?2")
                    .setParameter(1, workerId).setParameter(2, currentPrincipal.tenancyId())
                    .executeUpdate();
            return deleted > 0;
        });
    }
}
