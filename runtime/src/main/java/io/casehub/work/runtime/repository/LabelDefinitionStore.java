package io.casehub.work.runtime.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.casehub.platform.api.path.Path;
import io.casehub.work.runtime.model.LabelDefinition;

/**
 * KV-native store SPI for {@link LabelDefinition} persistence.
 *
 * <p>
 * Provides tenant-scoped CRUD operations for label definition management. Every query
 * is implicitly scoped to the current tenant via {@code CurrentPrincipal.tenancyId()}.
 *
 * <p>
 * <strong>CDI backend activation (four-tier priority ladder):</strong><br>
 * Tier 0: {@code @DefaultBean} (no-op fallback) — not applicable to this SPI.<br>
 * Tier 1: {@code @ApplicationScoped} (JPA/SQL, default) — {@code casehub-work} runtime.<br>
 * Tier 2: {@code @Alternative @Priority(1)} (MongoDB) — {@code casehub-work-persistence-mongodb}.<br>
 * Tier 3: {@code @Alternative @Priority(100)} (in-memory, ephemeral) — {@code casehub-work-persistence-memory}.<br>
 * Adding a backend module to the classpath activates it automatically — no consumer changes.
 * See the platform
 * <a href="https://github.com/casehubio/garden/blob/main/docs/protocols/universal/persistence-backend-cdi-priority.md">persistence-backend-cdi-priority</a>
 * protocol.
 */
public interface LabelDefinitionStore {

    /**
     * Persist or update a LabelDefinition and return the saved instance.
     *
     * <p>
     * On insert (entity has no {@code tenancyId}), stamps {@code tenancyId} from
     * {@code CurrentPrincipal.tenancyId()} before persist. On update, preserves
     * the existing {@code tenancyId} — definitions cannot be moved across tenants.
     *
     * @param definition the label definition to persist; must not be {@code null}
     * @return the persisted definition
     */
    LabelDefinition put(LabelDefinition definition);

    /**
     * Retrieve a LabelDefinition by its primary key, scoped to the current tenant.
     *
     * @param id the UUID primary key
     * @return an {@link Optional} containing the definition, or empty if not found or
     *         if the definition belongs to a different tenant
     */
    Optional<LabelDefinition> get(UUID id);

    /**
     * Find all definitions for a given vocabulary, scoped to the current tenant.
     *
     * @param vocabularyId the vocabulary UUID
     * @return list of label definitions; may be empty, never null
     */
    List<LabelDefinition> findByVocabularyId(UUID vocabularyId);

    /**
     * Find by exact path across all vocabularies, scoped to the current tenant.
     *
     * @param path the label path to search for
     * @return list of label definitions matching the path; may be empty, never null
     */
    List<LabelDefinition> findByPath(Path path);

    /**
     * Delete a LabelDefinition by ID, scoped to the current tenant.
     *
     * <p>
     * Returns {@code false} if the definition does not exist or belongs to a different tenant.
     *
     * @param id the UUID primary key
     * @return {@code true} if the definition was deleted, {@code false} if not found or
     *         not owned by the current tenant
     */
    boolean delete(UUID id);
}
