package io.casehub.work.runtime.repository.jpa;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemStatus;
import io.casehub.work.runtime.repository.CrossTenantWorkItemStore;

/**
 * Cross-tenant JPA implementation of {@link CrossTenantWorkItemStore}.
 *
 * <p>Does NOT inject {@link io.casehub.platform.api.identity.CurrentPrincipal}
 * and does NOT filter by {@code tenancyId} — queries return items from all tenants.
 * Only injected into system-level services via the {@code @CrossTenant} qualifier.
 */
@ApplicationScoped
public class JpaCrossTenantWorkItemStore implements CrossTenantWorkItemStore {

    @Override
    public List<WorkItem> findActiveWithDeadlines() {
        return WorkItem.find(
            "status NOT IN (?1) AND (expiresAt IS NOT NULL OR claimDeadline IS NOT NULL)",
            List.of(WorkItemStatus.COMPLETED, WorkItemStatus.REJECTED,
                    WorkItemStatus.CANCELLED, WorkItemStatus.EXPIRED,
                    WorkItemStatus.ESCALATED))
            .list();
    }
}
