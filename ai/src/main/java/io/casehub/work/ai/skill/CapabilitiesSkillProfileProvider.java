package io.casehub.work.ai.skill;

import io.casehub.work.api.SkillProfile;
import io.casehub.work.api.spi.SkillProfileProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.Set;

/**
 * Builds a {@link SkillProfile} by joining the worker's declared capability tags.
 *
 * <p>
 * Example output: {@code "legal, nda-review, gdpr"}.
 * Zero DB access — useful as a baseline when no richer profile data is available.
 * Activate by declaring {@code @Alternative @Priority(1)} on a producer or subclass.
 */
@ApplicationScoped
@Alternative
public class CapabilitiesSkillProfileProvider implements SkillProfileProvider {

    @Override
    public SkillProfile getProfile(final String workerId, final Set<String> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            return SkillProfile.ofNarrative("");
        }
        return SkillProfile.ofNarrative(String.join(", ", capabilities));
    }
}
