package io.casehub.work.progress.spi;

import io.casehub.work.progress.ProgressUpdatedEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ProgressEventStore {
    void append(ProgressUpdatedEvent event);

    List<ProgressUpdatedEvent> findByProgressId(UUID progressId);

    List<ProgressUpdatedEvent> findByProgressIdSince(UUID progressId, Instant since);

    List<ProgressUpdatedEvent> findByRootProgressIdSince(UUID rootProgressId, Instant since);
}
