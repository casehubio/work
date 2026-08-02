package io.casehub.work.progress.runtime.event;

import io.casehub.work.progress.ProgressUpdatedEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;

@ApplicationScoped
public class LocalProgressEventBroadcaster implements ProgressEventBroadcaster {

    private final BroadcastProcessor<ProgressUpdatedEvent> processor = BroadcastProcessor.create();

    void onProgressEvent(@Observes(during = TransactionPhase.AFTER_SUCCESS) ProgressUpdatedEvent event) {
        processor.onNext(event);
    }

    @Override
    public Multi<ProgressUpdatedEvent> stream(String tenancyId) {
        return processor.filter(e -> e.tenancyId().equals(tenancyId));
    }
}
