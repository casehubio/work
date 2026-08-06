package io.casehub.work.progress.runtime.event;

import io.casehub.work.progress.ProgressUpdatedEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import io.smallrye.mutiny.subscription.BackPressureFailure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;

@ApplicationScoped
public class LocalProgressEventBroadcaster implements ProgressEventBroadcaster {

    private final BroadcastProcessor<ProgressUpdatedEvent> processor = BroadcastProcessor.create();

    void onProgressEvent(@Observes(during = TransactionPhase.AFTER_SUCCESS) ProgressUpdatedEvent event) {
        try {
            processor.onNext(event);
        } catch (BackPressureFailure ignored) {
        }
    }

    @Override
    public Multi<ProgressUpdatedEvent> stream(String tenancyId) {
        return processor.toHotStream()
                .onOverflow().buffer(256)
                .filter(e -> e.tenancyId().equals(tenancyId));
    }
}
