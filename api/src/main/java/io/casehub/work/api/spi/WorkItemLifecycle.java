package io.casehub.work.api.spi;

import java.util.UUID;

public interface WorkItemLifecycle {

    void cancel(UUID id, String actorId, String reason);

    void complete(UUID id, String actorId, String resolution, String outcome,
                  String rationale, String planRef);

    default void complete(UUID id, String actorId, String resolution, String outcome) {
        complete(id, actorId, resolution, outcome, null, null);
    }
}
