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

import io.casehub.work.api.GroupStatus;
import io.casehub.work.api.WorkItemGroupLifecycleEvent;

@ExtendWith(MockitoExtension.class)
class WorkItemGroupLifecycleEmitterTest {

    @Mock
    Event<WorkItemGroupLifecycleEvent> delegate;

    private WorkItemGroupLifecycleEmitter emitter;

    @BeforeEach
    void setUp() {
        emitter = new WorkItemGroupLifecycleEmitter();
        emitter.delegate = delegate;
    }

    @Test
    void emit_callsBothFireAndFireAsync() {
        when(delegate.fireAsync(any())).thenReturn(CompletableFuture.completedFuture(null));
        final WorkItemGroupLifecycleEvent event = sampleGroupEvent();

        emitter.emit(event);

        verify(delegate).fire(event);
        verify(delegate).fireAsync(event);
    }

    @Test
    void emit_fireAsyncFailure_doesNotPropagate() {
        final CompletableFuture<WorkItemGroupLifecycleEvent> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("observer failure"));
        when(delegate.fireAsync(any())).thenReturn(failed);

        final WorkItemGroupLifecycleEvent event = sampleGroupEvent();

        emitter.emit(event);

        verify(delegate).fire(event);
        verify(delegate).fireAsync(event);
    }

    private WorkItemGroupLifecycleEvent sampleGroupEvent() {
        return WorkItemGroupLifecycleEvent.of(
                UUID.randomUUID(), UUID.randomUUID(),
                3, 2, 1, 0,
                GroupStatus.IN_PROGRESS, "caller-ref", "tenant-1");
    }
}
