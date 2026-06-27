package io.casehub.work.runtime.event;

import static org.mockito.Mockito.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import jakarta.enterprise.event.Event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.api.WorkItemStatus;

@ExtendWith(MockitoExtension.class)
class WorkItemLifecycleEmitterTest {

    @Mock
    Event<WorkItemLifecycleEvent> delegate;

    private WorkItemLifecycleEmitter emitter;

    @BeforeEach
    void setUp() {
        emitter = new WorkItemLifecycleEmitter();
        emitter.delegate = delegate;
    }

    @Test
    void emit_callsBothFireAndFireAsync() {
        when(delegate.fireAsync(any())).thenReturn(CompletableFuture.completedFuture(null));
        final WorkItemLifecycleEvent event = sampleEvent("CREATED");

        emitter.emit(event);

        verify(delegate).fire(event);
        verify(delegate).fireAsync(event);
    }

    @Test
    void emit_fireAsyncFailure_doesNotPropagate() {
        final CompletableFuture<WorkItemLifecycleEvent> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("observer failure"));
        when(delegate.fireAsync(any())).thenReturn(failed);

        final WorkItemLifecycleEvent event = sampleEvent("COMPLETED");

        emitter.emit(event);

        verify(delegate).fire(event);
        verify(delegate).fireAsync(event);
    }

    private WorkItemLifecycleEvent sampleEvent(final String name) {
        final WorkItem wi = new WorkItem();
        wi.id = UUID.randomUUID();
        wi.status = WorkItemStatus.PENDING;
        return WorkItemLifecycleEvent.of(name, wi, "test", null);
    }
}
