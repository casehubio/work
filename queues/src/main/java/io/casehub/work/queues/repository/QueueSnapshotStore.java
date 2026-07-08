package io.casehub.work.queues.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.casehub.work.queues.model.QueueSnapshot;

public interface QueueSnapshotStore {

    QueueSnapshot put(QueueSnapshot snapshot);

    List<QueueSnapshot> findByQueueAndPeriod(UUID queueViewId, Instant from, Instant to);

    Map<UUID, Instant> findLatestSnapshotTimes(Collection<UUID> queueViewIds);

    void deleteOlderThan(Instant cutoff);
}
