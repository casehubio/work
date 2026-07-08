package io.casehub.work.queues.repository.jpa;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import io.casehub.work.queues.model.QueueView;
import io.casehub.work.queues.repository.CrossTenantQueueViewStore;
import io.casehub.work.runtime.repository.jpa.TenantAwareStore;

@ApplicationScoped
public class JpaCrossTenantQueueViewStore extends TenantAwareStore implements CrossTenantQueueViewStore {

    @Override
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public List<String> findDistinctTenancyIds() {
        return withCrossTenantQuery(() ->
                QueueView.getEntityManager()
                        .createQuery("SELECT DISTINCT qv.tenancyId FROM QueueView qv", String.class)
                        .getResultList());
    }
}
