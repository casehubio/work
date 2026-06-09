package io.casehub.work.runtime.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.casehub.work.runtime.filter.FilterRule;

/**
 * KV-native store SPI for {@link FilterRule} persistence.
 *
 * <p>
 * Provides tenant-scoped CRUD operations for filter rule management. Every query
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
public interface FilterRuleStore {

    /**
     * Persist or update a FilterRule and return the saved instance.
     *
     * <p>
     * On insert (entity has no {@code tenancyId}), stamps {@code tenancyId} from
     * {@code CurrentPrincipal.tenancyId()} before persist. On update, preserves
     * the existing {@code tenancyId} — rules cannot be moved across tenants.
     *
     * @param rule the filter rule to persist; must not be {@code null}
     * @return the persisted rule
     */
    FilterRule put(FilterRule rule);

    /**
     * Retrieve a FilterRule by its primary key, scoped to the current tenant.
     *
     * @param id the UUID primary key
     * @return an {@link Optional} containing the rule, or empty if not found or
     *         if the rule belongs to a different tenant
     */
    Optional<FilterRule> get(UUID id);

    /**
     * Returns all enabled filter rules, scoped to the current tenant, ordered by
     * creation time ascending.
     *
     * @return list of enabled FilterRule entities; may be empty, never null
     */
    List<FilterRule> allEnabled();

    /**
     * Return all filter rules (enabled and disabled) visible to the current tenant,
     * ordered by creation time ascending.
     *
     * @return list of all FilterRule entities; may be empty, never null
     */
    List<FilterRule> scanAll();

    /**
     * Delete a FilterRule by ID, scoped to the current tenant.
     *
     * <p>
     * Returns {@code false} if the rule does not exist or belongs to a different tenant.
     *
     * @param id the UUID primary key
     * @return {@code true} if the rule was deleted, {@code false} if not found or
     *         not owned by the current tenant
     */
    boolean delete(UUID id);
}
