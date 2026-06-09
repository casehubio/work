package io.casehub.work.queues.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.casehub.work.queues.model.QueueView;

/**
 * Store SPI for {@link QueueView} persistence.
 *
 * <p>All queries are scoped to the current tenant via the implementation's
 * {@link io.casehub.platform.api.identity.CurrentPrincipal}.
 */
public interface QueueViewStore {

    /**
     * Persist or update a QueueView and return the saved instance.
     * Stamps {@code tenancyId} from the current principal on insert when null.
     *
     * @param view the queue view to persist; must not be {@code null}
     * @return the persisted queue view
     */
    QueueView put(QueueView view);

    /**
     * Retrieve a QueueView by its primary key, scoped to the current tenant.
     *
     * @param id the UUID primary key
     * @return an {@link Optional} containing the queue view, or empty if not found
     */
    Optional<QueueView> get(UUID id);

    /**
     * Return all QueueViews for the current tenant.
     *
     * @return unordered list of all queue views; never null
     */
    List<QueueView> scanAll();

    /**
     * Delete a QueueView by its primary key, scoped to the current tenant.
     *
     * @param id the UUID primary key
     * @return {@code true} if the entity was deleted, {@code false} if not found
     */
    boolean delete(UUID id);
}
