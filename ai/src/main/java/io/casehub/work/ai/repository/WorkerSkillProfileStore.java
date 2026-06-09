package io.casehub.work.ai.repository;

import java.util.List;
import java.util.Optional;

import io.casehub.work.ai.skill.WorkerSkillProfile;

/**
 * Store SPI for {@link WorkerSkillProfile} persistence.
 *
 * <p>All queries are scoped to the current tenant via the implementation's
 * {@link io.casehub.platform.api.identity.CurrentPrincipal}.
 *
 * <p>The primary key is the {@code workerId} string, not a UUID.
 *
 * <p>
 * <strong>CDI backend activation:</strong><br>
 * Tier 1: {@code @ApplicationScoped} (JPA/SQL, default) — {@code casehub-work-ai}.<br>
 * Tier 3: {@code @Alternative @Priority(100)} (in-memory, ephemeral) — {@code casehub-work-persistence-memory}.<br>
 * No Tier 2 (MongoDB) exists yet.
 */
public interface WorkerSkillProfileStore {

    /**
     * Persist or update a worker skill profile and return the saved instance.
     * Stamps {@code tenancyId} from the current principal on insert when null.
     *
     * @param profile the profile to persist; must not be {@code null}
     * @return the persisted profile
     */
    WorkerSkillProfile put(WorkerSkillProfile profile);

    /**
     * Retrieve a worker skill profile by workerId, scoped to the current tenant.
     *
     * @param workerId the worker identifier (primary key)
     * @return an {@link Optional} containing the profile, or empty if not found
     */
    Optional<WorkerSkillProfile> get(String workerId);

    /**
     * Return all worker skill profiles for the current tenant.
     *
     * @return list of all profiles; never null
     */
    List<WorkerSkillProfile> scanAll();

    /**
     * Delete a worker skill profile by workerId, scoped to the current tenant.
     *
     * @param workerId the worker identifier (primary key)
     * @return {@code true} if the entity was deleted, {@code false} if not found
     */
    boolean delete(String workerId);
}
