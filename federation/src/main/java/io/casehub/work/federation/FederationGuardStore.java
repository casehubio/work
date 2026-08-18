package io.casehub.work.federation;

import io.casehub.work.api.WorkItem;
import io.casehub.work.api.WorkItemQuery;
import io.casehub.work.api.spi.WorkItemStore;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptor;

@Decorator
@Priority(Interceptor.Priority.APPLICATION)
public class FederationGuardStore implements WorkItemStore {

    @Delegate
    @Inject
    @Any
    WorkItemStore delegate;

    @Override
    public WorkItem put(WorkItem item) {
        if (item.originServiceId() != null && !FederationSyncContext.isActive()) {
            throw new FederatedWorkItemMutationException(item.id());
        }
        return delegate.put(item);
    }

    @Override
    public Optional<WorkItem> get(UUID id) {
        return delegate.get(id);
    }

    @Override
    public List<WorkItem> scan(WorkItemQuery query) {
        return delegate.scan(query);
    }
}
