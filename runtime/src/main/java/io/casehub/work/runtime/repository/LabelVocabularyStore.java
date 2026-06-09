package io.casehub.work.runtime.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.casehub.work.runtime.model.LabelVocabulary;

/**
 * KV-native store SPI for {@link LabelVocabulary} persistence.
 *
 * <p>
 * Provides tenant-scoped CRUD operations for label vocabulary management. Every query
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
public interface LabelVocabularyStore {

    /**
     * Persist or update a LabelVocabulary and return the saved instance.
     *
     * <p>
     * On insert (entity has no {@code tenancyId}), stamps {@code tenancyId} from
     * {@code CurrentPrincipal.tenancyId()} before persist. On update, preserves
     * the existing {@code tenancyId} — vocabularies cannot be moved across tenants.
     *
     * @param vocabulary the vocabulary to persist; must not be {@code null}
     * @return the persisted vocabulary
     */
    LabelVocabulary put(LabelVocabulary vocabulary);

    /**
     * Retrieve a LabelVocabulary by its primary key, scoped to the current tenant.
     *
     * @param id the UUID primary key
     * @return an {@link Optional} containing the vocabulary, or empty if not found or
     *         if the vocabulary belongs to a different tenant
     */
    Optional<LabelVocabulary> get(UUID id);

    /**
     * Return all vocabularies visible to the current tenant.
     *
     * @return list of vocabularies; may be empty, never null
     */
    List<LabelVocabulary> scanAll();

    /**
     * Delete a LabelVocabulary by ID, scoped to the current tenant.
     *
     * <p>
     * Does NOT cascade-delete associated {@code LabelDefinition} entries.
     * Returns {@code false} if the vocabulary does not exist or belongs to a different tenant.
     *
     * @param id the UUID primary key
     * @return {@code true} if the vocabulary was deleted, {@code false} if not found or
     *         not owned by the current tenant
     */
    boolean delete(UUID id);
}
