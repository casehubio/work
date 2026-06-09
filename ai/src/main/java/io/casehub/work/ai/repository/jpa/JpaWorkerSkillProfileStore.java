package io.casehub.work.ai.repository.jpa;

import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.work.ai.repository.WorkerSkillProfileStore;
import io.casehub.work.ai.skill.WorkerSkillProfile;

/**
 * Default JPA/Panache implementation of {@link WorkerSkillProfileStore}.
 *
 * <p>Every query is scoped to the current tenant via {@link CurrentPrincipal#tenancyId()}.
 * The {@link #put} method stamps {@code tenancyId} from the principal on insert when
 * the entity does not already carry one.
 */
@ApplicationScoped
public class JpaWorkerSkillProfileStore implements WorkerSkillProfileStore {

    @Inject
    CurrentPrincipal currentPrincipal;

    @Override
    public WorkerSkillProfile put(final WorkerSkillProfile profile) {
        if (profile.tenancyId == null) {
            profile.tenancyId = currentPrincipal.tenancyId();
        }
        profile.persistAndFlush();
        return profile;
    }

    @Override
    public Optional<WorkerSkillProfile> get(final String workerId) {
        return WorkerSkillProfile.find("workerId = ?1 AND tenancyId = ?2",
                workerId, currentPrincipal.tenancyId())
                .firstResultOptional();
    }

    @Override
    public List<WorkerSkillProfile> scanAll() {
        return WorkerSkillProfile.list("tenancyId", currentPrincipal.tenancyId());
    }

    @Override
    public boolean delete(final String workerId) {
        final long deleted = WorkerSkillProfile.delete("workerId = ?1 AND tenancyId = ?2",
                workerId, currentPrincipal.tenancyId());
        return deleted > 0;
    }
}
