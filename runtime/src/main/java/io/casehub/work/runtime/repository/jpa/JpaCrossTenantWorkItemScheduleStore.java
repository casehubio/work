package io.casehub.work.runtime.repository.jpa;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.work.runtime.model.WorkItemSchedule;
import io.casehub.work.runtime.repository.CrossTenantWorkItemScheduleStore;

/**
 * Cross-tenant JPA implementation of {@link CrossTenantWorkItemScheduleStore}.
 *
 * <p>Does NOT inject {@link io.casehub.platform.api.identity.CurrentPrincipal}
 * and does NOT filter by {@code tenancyId} — queries return schedules from all tenants.
 * Only injected into system-level services via the {@code @CrossTenant} qualifier.
 */
@ApplicationScoped
public class JpaCrossTenantWorkItemScheduleStore implements CrossTenantWorkItemScheduleStore {

    @Override
    public List<WorkItemSchedule> findActive() {
        return WorkItemSchedule.find("active = true").list();
    }
}
