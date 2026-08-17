package io.casehub.work.graphql;

import io.casehub.work.api.WorkItemLifecycleEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.BackPressureStrategy;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@ApplicationScoped
public class WorkItemEventPublisher {

    private final List<MultiEmitter<? super WorkItemLifecycleEvent>> emitters =
        new CopyOnWriteArrayList<>();

    void onLifecycleEvent(@ObservesAsync WorkItemLifecycleEvent event) {
        for (var emitter : emitters) {
            emitter.emit(event);
        }
    }

    public Multi<WorkItemLifecycleEvent> lifecycleStream() {
        return Multi.createFrom().<WorkItemLifecycleEvent>emitter(emitter -> {
            emitters.add(emitter);
            emitter.onTermination(() -> emitters.remove(emitter));
        }, BackPressureStrategy.DROP);
    }
}
