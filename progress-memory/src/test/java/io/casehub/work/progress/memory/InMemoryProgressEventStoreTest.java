package io.casehub.work.progress.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.work.progress.ProgressChangeType;
import io.casehub.work.progress.ProgressStatus;
import io.casehub.work.progress.ProgressUpdatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryProgressEventStoreTest {

    private final InMemoryProgressEventStore store = new InMemoryProgressEventStore();
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        store.clear();
    }

    private ProgressUpdatedEvent event(UUID progressId, UUID rootId, Instant timestamp) {
        return new ProgressUpdatedEvent(UUID.randomUUID(), progressId, "t1", "workitem", "wi-1",
                                        null, rootId, "percentage", null, null,
                                        ProgressStatus.ACTIVE, ProgressChangeType.STATE_UPDATED, timestamp, null);
    }

    @Test
    void appendAndFindByProgressId() {
        UUID pid = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        store.append(event(pid, rootId, Instant.now()));
        store.append(event(pid, rootId, Instant.now().plusSeconds(1)));

        var results = store.findByProgressId(pid);
        assertThat(results).hasSize(2);
    }

    @Test
    void findByProgressIdSinceFilters() {
        UUID pid = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2026-01-01T01:00:00Z");
        Instant t3 = Instant.parse("2026-01-01T02:00:00Z");

        store.append(event(pid, rootId, t1));
        store.append(event(pid, rootId, t2));
        store.append(event(pid, rootId, t3));

        var results = store.findByProgressIdSince(pid, t1);
        assertThat(results).hasSize(2);
    }

    @Test
    void findByRootProgressIdSince() {
        UUID root = UUID.randomUUID();
        UUID child1 = UUID.randomUUID();
        UUID child2 = UUID.randomUUID();
        Instant since = Instant.parse("2026-01-01T00:00:00Z");

        store.append(event(child1, root, since.plusSeconds(1)));
        store.append(event(child2, root, since.plusSeconds(2)));
        store.append(event(UUID.randomUUID(), UUID.randomUUID(), since.plusSeconds(3)));

        var results = store.findByRootProgressIdSince(root, since);
        assertThat(results).hasSize(2);
    }

    @Test
    void orderedByTimestamp() {
        UUID pid = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        Instant t1 = Instant.parse("2026-01-01T02:00:00Z");
        Instant t2 = Instant.parse("2026-01-01T01:00:00Z");

        store.append(event(pid, rootId, t1));
        store.append(event(pid, rootId, t2));

        var results = store.findByProgressId(pid);
        assertThat(results.get(0).timestamp()).isBefore(results.get(1).timestamp());
    }

    @Test
    void findLastEventAtOrBefore_returnsEventAtExactCutoff() {
        UUID    progressId = UUID.randomUUID();
        UUID    rootId     = UUID.randomUUID();
        Instant t1         = Instant.parse("2026-01-01T10:00:00Z");
        Instant t2         = Instant.parse("2026-01-01T11:00:00Z");
        store.append(event(progressId, rootId, t1));
        store.append(event(progressId, rootId, t2));

        Optional<ProgressUpdatedEvent> result = store.findLastEventAtOrBefore(progressId, t2);
        assertThat(result).isPresent();
        assertThat(result.get().timestamp()).isEqualTo(t2);
    }

    @Test
    void findLastEventAtOrBefore_returnsLatestBeforeCutoff() {
        UUID    progressId = UUID.randomUUID();
        UUID    rootId     = UUID.randomUUID();
        Instant t1         = Instant.parse("2026-01-01T10:00:00Z");
        Instant t2         = Instant.parse("2026-01-01T11:00:00Z");
        Instant t3         = Instant.parse("2026-01-01T12:00:00Z");
        store.append(event(progressId, rootId, t1));
        store.append(event(progressId, rootId, t2));
        store.append(event(progressId, rootId, t3));

        Instant                        cutoff = Instant.parse("2026-01-01T11:30:00Z");
        Optional<ProgressUpdatedEvent> result = store.findLastEventAtOrBefore(progressId, cutoff);
        assertThat(result).isPresent();
        assertThat(result.get().timestamp()).isEqualTo(t2);
    }

    @Test
    void findLastEventAtOrBefore_noEventBeforeCutoff_returnsEmpty() {
        UUID    progressId = UUID.randomUUID();
        UUID    rootId     = UUID.randomUUID();
        Instant t1         = Instant.parse("2026-01-01T10:00:00Z");
        store.append(event(progressId, rootId, t1));

        Instant cutoff = Instant.parse("2026-01-01T09:00:00Z");
        assertThat(store.findLastEventAtOrBefore(progressId, cutoff)).isEmpty();
    }

    @Test
    void findLastEventAtOrBefore_filteredByProgressId() {
        UUID    id1    = UUID.randomUUID();
        UUID    id2    = UUID.randomUUID();
        UUID    rootId = UUID.randomUUID();
        Instant t1     = Instant.parse("2026-01-01T10:00:00Z");
        Instant t2     = Instant.parse("2026-01-01T11:00:00Z");
        store.append(event(id1, rootId, t1));
        store.append(event(id2, rootId, t2));

        Instant                        cutoff = Instant.parse("2026-01-01T12:00:00Z");
        Optional<ProgressUpdatedEvent> result = store.findLastEventAtOrBefore(id1, cutoff);
        assertThat(result).isPresent();
        assertThat(result.get().progressId()).isEqualTo(id1);
    }
}
