package io.casehub.work.queues.repository;

import java.util.Optional;
import java.util.UUID;

import io.casehub.work.queues.model.FilterChain;

/**
 * Store SPI for {@link FilterChain} persistence.
 *
 * <p>All queries are scoped to the current tenant via the implementation's
 * {@link io.casehub.platform.api.identity.CurrentPrincipal}.
 */
public interface FilterChainStore {

    /**
     * Persist or update a filter chain and return the saved instance.
     * Stamps {@code tenancyId} from the current principal on insert when null.
     *
     * @param chain the filter chain to persist; must not be {@code null}
     * @return the persisted filter chain
     */
    FilterChain put(FilterChain chain);

    /**
     * Find the FilterChain for the given filterId, scoped to the current tenant.
     *
     * @param filterId the filter whose chain to look up
     * @return an {@link Optional} containing the chain, or empty if not found
     */
    Optional<FilterChain> findByFilterId(UUID filterId);

    /**
     * Find or create a FilterChain for the given filterId, scoped to the current tenant.
     * If no chain exists, one is created with the current tenant's tenancyId and persisted.
     *
     * @param filterId the filter whose chain to look up or create
     * @return the existing or newly created FilterChain; never null
     */
    FilterChain findOrCreateForFilter(UUID filterId);
}
