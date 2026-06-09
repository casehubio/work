package io.casehub.work.queues.repository.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.work.queues.model.WorkItemFilter;
import io.casehub.work.queues.repository.WorkItemFilterStore;

/**
 * Default JPA/Panache implementation of {@link WorkItemFilterStore}.
 *
 * <p>Every query is scoped to the current tenant via {@link CurrentPrincipal#tenancyId()}.
 * The {@link #put} method stamps {@code tenancyId} from the principal on insert when
 * the entity does not already carry one.
 */
@ApplicationScoped
public class JpaWorkItemFilterStore implements WorkItemFilterStore {

    @Inject
    CurrentPrincipal currentPrincipal;

    @Override
    public WorkItemFilter put(final WorkItemFilter filter) {
        if (filter.tenancyId == null) {
            filter.tenancyId = currentPrincipal.tenancyId();
        }
        filter.persistAndFlush();
        return filter;
    }

    @Override
    public Optional<WorkItemFilter> get(final UUID id) {
        return WorkItemFilter.find("id = ?1 AND tenancyId = ?2", id, currentPrincipal.tenancyId())
                .firstResultOptional();
    }

    @Override
    public List<WorkItemFilter> findActive() {
        return WorkItemFilter.list("active = true AND conditionLanguage != 'lambda' AND tenancyId = ?1",
                currentPrincipal.tenancyId());
    }

    @Override
    public List<WorkItemFilter> scanAll() {
        return WorkItemFilter.list("tenancyId", currentPrincipal.tenancyId());
    }

    @Override
    public boolean delete(final UUID id) {
        long deleted = WorkItemFilter.delete("id = ?1 AND tenancyId = ?2", id, currentPrincipal.tenancyId());
        return deleted > 0;
    }
}
