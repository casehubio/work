package io.casehub.work.runtime.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Instance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.casehub.work.api.WorkItemStatusEvent;
import io.casehub.work.api.spi.WorkItemObserver;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.api.WorkItemStatus;

@ExtendWith(MockitoExtension.class)
class WorkItemLifecycleEmitterTest {

    @Mock
    Event<WorkItemLifecycleEvent> delegate;

    @Mock
    Instance<WorkItemObserver> observers;

    private WorkItemLifecycleEmitter emitter;

    @BeforeEach
    void setUp() {
        emitter = new WorkItemLifecycleEmitter();
        emitter.delegate = delegate;
        emitter.observers = observers;
    }

    @Test
    void emit_callsBothFireAndFireAsync() {
        when(delegate.fireAsync(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(observers.isUnsatisfied()).thenReturn(true);
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
        when(observers.isUnsatisfied()).thenReturn(true);

        final WorkItemLifecycleEvent event = sampleEvent("COMPLETED");

        emitter.emit(event);

        verify(delegate).fire(event);
        verify(delegate).fireAsync(event);
    }

    @Test
    void emit_dispatchesToSpiObservers() {
        when(delegate.fireAsync(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(observers.isUnsatisfied()).thenReturn(false);
        final List<WorkItemStatusEvent> received = new CopyOnWriteArrayList<>();
        final WorkItemObserver observer = received::add;
        when(observers.iterator()).thenReturn(List.of(observer).iterator());

        final WorkItemLifecycleEvent event = sampleEvent("COMPLETED");
        emitter.emit(event);

        assertThat(received).hasSize(1);
        assertThat(received.get(0).workItemId()).isEqualTo(event.workItemId());
        assertThat(received.get(0).status()).isEqualTo(event.status());
        assertThat(received.get(0).actor()).isEqualTo("test");
    }

    @Test
    void emit_observerFailure_doesNotPropagate() {
        when(delegate.fireAsync(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(observers.isUnsatisfied()).thenReturn(false);
        final WorkItemObserver failingObserver = e -> { throw new RuntimeException("boom"); };
        when(observers.iterator()).thenReturn(List.of(failingObserver).iterator());

        final WorkItemLifecycleEvent event = sampleEvent("CANCELLED");

        emitter.emit(event);

        verify(delegate).fire(event);
    }

    @Test
    void emit_noObservers_skipsDispatch() {
        when(delegate.fireAsync(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(observers.isUnsatisfied()).thenReturn(true);

        emitter.emit(sampleEvent("CREATED"));

        verify(observers, never()).iterator();
    }

    private WorkItemLifecycleEvent sampleEvent(final String name) {
        final WorkItem wi = new WorkItem();
        wi.id = UUID.randomUUID();
        wi.status = WorkItemStatus.PENDING;
        return WorkItemLifecycleEvent.of(name, wi, "test", null);
    }
}
