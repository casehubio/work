package io.casehub.work.queues.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.work.runtime.repository.CrossTenant;
import io.casehub.work.runtime.service.WorkSystem;
import io.casehub.work.queues.repository.CrossTenantQueueViewStore;

@ApplicationScoped
public class QueueCrossTenantProducer {

    @Inject
    @WorkSystem
    CurrentPrincipal systemPrincipal;

    @Inject
    CrossTenantQueueViewStore crossTenantQueueViewStore;

    @Produces
    @CrossTenant
    @ApplicationScoped
    public CrossTenantQueueViewStore produceQueueViewStore() {
        if (!systemPrincipal.isCrossTenantAdmin()) {
            throw new IllegalStateException(
                    "SystemCurrentPrincipal.isCrossTenantAdmin() must return true");
        }
        return crossTenantQueueViewStore;
    }
}
