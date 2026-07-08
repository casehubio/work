package io.casehub.work.queues.repository;

import java.util.List;

public interface CrossTenantQueueViewStore {

    List<String> findDistinctTenancyIds();
}
