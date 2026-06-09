package io.casehub.work.runtime.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import io.casehub.work.runtime.model.WorkItemTemplate;

/**
 * KV-native store SPI for {@link WorkItemTemplate} persistence.
 *
 * <p>
 * Provides tenant-scoped CRUD operations for template management. Every query
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
public interface WorkItemTemplateStore {

    /**
     * Persist or update a WorkItemTemplate and return the saved instance.
     *
     * <p>
     * On insert (entity has no {@code tenancyId}), stamps {@code tenancyId} from
     * {@code CurrentPrincipal.tenancyId()} before persist. On update, preserves
     * the existing {@code tenancyId} — templates cannot be moved across tenants.
     *
     * @param template the template to persist; must not be {@code null}
     * @return the persisted template
     */
    WorkItemTemplate put(WorkItemTemplate template);

    /**
     * Retrieve a WorkItemTemplate by its primary key, scoped to the current tenant.
     *
     * @param id the UUID primary key
     * @return an {@link Optional} containing the template, or empty if not found or
     *         if the template belongs to a different tenant
     */
    Optional<WorkItemTemplate> get(UUID id);

    /**
     * Find a template by exact name, scoped to the current tenant.
     *
     * @param name the template name to look up; must not be {@code null}
     * @return an {@link Optional} containing the matching template, or empty if not found
     */
    Optional<WorkItemTemplate> getByName(String name);

    /**
     * Return all templates visible to the current tenant, ordered by name ascending.
     *
     * @return list of templates; may be empty, never null
     */
    List<WorkItemTemplate> scanAll();

    /**
     * Delete a WorkItemTemplate by ID, scoped to the current tenant.
     *
     * <p>
     * Does NOT cascade-delete WorkItems previously instantiated from this template.
     * Returns {@code false} if the template does not exist or belongs to a different tenant.
     *
     * @param id the UUID primary key
     * @return {@code true} if the template was deleted, {@code false} if not found or
     *         not owned by the current tenant
     */
    boolean delete(UUID id);
}
