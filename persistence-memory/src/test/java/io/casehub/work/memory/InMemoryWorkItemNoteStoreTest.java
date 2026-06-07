package io.casehub.work.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.work.runtime.model.WorkItemNote;

class InMemoryWorkItemNoteStoreTest {

    private InMemoryWorkItemNoteStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryWorkItemNoteStore();
    }

    @Test
    void append_concurrentWrites_nothingLost() throws Exception {
        final int threads = 8;
        final int perThread = 100;
        final var latch = new CountDownLatch(1);
        final var executor = Executors.newFixedThreadPool(threads);
        final var futures = new java.util.ArrayList<Future<?>>();
        final UUID workItemId = UUID.randomUUID();

        for (int t = 0; t < threads; t++) {
            futures.add(executor.submit(() -> {
                try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                for (int i = 0; i < perThread; i++) {
                    final WorkItemNote note = new WorkItemNote();
                    note.workItemId = workItemId;
                    note.content = "note";
                    note.author = "test";
                    store.append(note);
                }
            }));
        }
        latch.countDown();
        for (var f : futures) { f.get(5, TimeUnit.SECONDS); }
        executor.shutdown();

        assertThat(store.findAll()).hasSize(threads * perThread);
    }
}
