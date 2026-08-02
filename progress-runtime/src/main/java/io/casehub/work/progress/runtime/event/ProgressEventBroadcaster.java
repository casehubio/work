package io.casehub.work.progress.runtime.event;

import io.casehub.work.progress.ProgressUpdatedEvent;
import io.smallrye.mutiny.Multi;

public interface ProgressEventBroadcaster {
    Multi<ProgressUpdatedEvent> stream(String tenancyId);
}
