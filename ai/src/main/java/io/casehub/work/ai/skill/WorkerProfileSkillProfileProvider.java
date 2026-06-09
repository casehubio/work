package io.casehub.work.ai.skill;

import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.work.ai.repository.WorkerSkillProfileStore;
import io.casehub.work.api.SkillProfile;
import io.casehub.work.api.SkillProfileProvider;

/**
 * Reads a worker's skill profile from the {@link WorkerSkillProfile} entity.
 *
 * <p>
 * Falls back to empty narrative when no profile exists.
 * Activate by declaring as {@code @Alternative @Priority(1)}.
 */
@ApplicationScoped
public class WorkerProfileSkillProfileProvider implements SkillProfileProvider {

    @Inject
    WorkerSkillProfileStore profileStore;

    private final Function<String, Optional<WorkerSkillProfile>> finder;

    /** CDI constructor — delegates to injected store. */
    public WorkerProfileSkillProfileProvider() {
        this.finder = null; // CDI path uses profileStore directly
    }

    /** Test constructor — injectable finder for unit testing without CDI. */
    WorkerProfileSkillProfileProvider(final Function<String, Optional<WorkerSkillProfile>> finder) {
        this.finder = finder;
    }

    @Override
    public SkillProfile getProfile(final String workerId, final Set<String> capabilities) {
        final Function<String, Optional<WorkerSkillProfile>> lookup =
                finder != null ? finder : profileStore::get;
        return lookup.apply(workerId)
                .map(p -> SkillProfile.ofNarrative(p.narrative != null ? p.narrative : ""))
                .orElse(SkillProfile.ofNarrative(""));
    }
}
