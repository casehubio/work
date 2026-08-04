package io.casehub.work.progress.memory;

import io.casehub.work.progress.ProgressUpdatedEvent;
import io.casehub.work.progress.spi.ProgressEventStore;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Alternative
@Priority(100)
@ApplicationScoped
public class InMemoryProgressEventStore implements ProgressEventStore {

    private final CopyOnWriteArrayList<ProgressUpdatedEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void append(ProgressUpdatedEvent event) {
        events.add(event);
    }

    @Override
    public Optional<ProgressUpdatedEvent> findById(UUID eventId) {
        return events.stream()
                     .filter(e -> e.id().equals(eventId))
                     .findFirst();
    }


    @Override
    public List<ProgressUpdatedEvent> findByProgressId(UUID progressId) {
        return events.stream()
                .filter(e -> e.progressId().equals(progressId))
                .sorted((a, b) -> a.timestamp().compareTo(b.timestamp()))
                .toList();
    }

    @Override
    public List<ProgressUpdatedEvent> findByProgressIdSince(UUID progressId, Instant since) {
        return events.stream()
                .filter(e -> e.progressId().equals(progressId) && e.timestamp().isAfter(since))
                .sorted((a, b) -> a.timestamp().compareTo(b.timestamp()))
                .toList();
    }

    @Override
    public List<ProgressUpdatedEvent> findByRootProgressIdSince(UUID rootProgressId, Instant since) {
        return events.stream()
                .filter(e -> rootProgressId.equals(e.rootProgressId()) && e.timestamp().isAfter(since))
                .sorted((a, b) -> a.timestamp().compareTo(b.timestamp()))
                .toList();
    }

    public void clear() {
        events.clear();
    }
}
