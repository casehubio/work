package io.casehub.work.queues.repository.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.work.queues.model.QueueView;
import io.casehub.work.queues.repository.QueueViewStore;

/**
 * Default JPA/Panache implementation of {@link QueueViewStore}.
 *
 * <p>Every query is scoped to the current tenant via {@link CurrentPrincipal#tenancyId()}.
 * The {@link #put} method stamps {@code tenancyId} from the principal on insert when
 * the entity does not already carry one.
 */
@ApplicationScoped
public class JpaQueueViewStore implements QueueViewStore {

    @Inject
    CurrentPrincipal currentPrincipal;

    @Override
    public QueueView put(final QueueView view) {
        if (view.tenancyId == null) {
            view.tenancyId = currentPrincipal.tenancyId();
        }
        view.persistAndFlush();
        return view;
    }

    @Override
    public Optional<QueueView> get(final UUID id) {
        return QueueView.find("id = ?1 AND tenancyId = ?2", id, currentPrincipal.tenancyId())
                .firstResultOptional();
    }

    @Override
    public List<QueueView> scanAll() {
        return QueueView.list("tenancyId", currentPrincipal.tenancyId());
    }

    @Override
    public boolean delete(final UUID id) {
        long deleted = QueueView.delete("id = ?1 AND tenancyId = ?2", id, currentPrincipal.tenancyId());
        return deleted > 0;
    }
}
