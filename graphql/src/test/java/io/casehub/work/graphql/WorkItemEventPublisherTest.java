package io.casehub.work.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.work.api.WorkItem;
import io.casehub.work.api.WorkItemLifecycleEvent;
import io.casehub.work.api.WorkItemStatus;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkItemEventPublisherTest {

    private WorkItemEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new WorkItemEventPublisher();
    }

    @Test
    void lifecycle_event_reaches_subscriber() {
        var subscriber = publisher.lifecycleStream()
            .subscribe().withSubscriber(AssertSubscriber.create(10));

        var event = lifecycleEvent(UUID.randomUUID(), "CREATED");
        publisher.onLifecycleEvent(event);

        subscriber.awaitItems(1, Duration.ofSeconds(1));
        assertThat(subscriber.getItems()).containsExactly(event);
    }

    @Test
    void multiple_subscribers_each_receive() {
        var sub1 = publisher.lifecycleStream()
            .subscribe().withSubscriber(AssertSubscriber.create(10));
        var sub2 = publisher.lifecycleStream()
            .subscribe().withSubscriber(AssertSubscriber.create(10));

        var event = lifecycleEvent(UUID.randomUUID(), "ASSIGNED");
        publisher.onLifecycleEvent(event);

        sub1.awaitItems(1, Duration.ofSeconds(1));
        sub2.awaitItems(1, Duration.ofSeconds(1));
        assertThat(sub1.getItems()).hasSize(1);
        assertThat(sub2.getItems()).hasSize(1);
    }

    @Test
    void disconnected_subscriber_stops_receiving() {
        var subscriber = publisher.lifecycleStream()
            .subscribe().withSubscriber(AssertSubscriber.create(10));

        publisher.onLifecycleEvent(lifecycleEvent(UUID.randomUUID(), "CREATED"));
        subscriber.awaitItems(1, Duration.ofSeconds(1));
        subscriber.cancel();

        publisher.onLifecycleEvent(lifecycleEvent(UUID.randomUUID(), "COMPLETED"));
        assertThat(subscriber.getItems()).hasSize(1);
    }

    @Test
    void events_before_subscriber_are_dropped() {
        publisher.onLifecycleEvent(lifecycleEvent(UUID.randomUUID(), "CREATED"));

        var subscriber = publisher.lifecycleStream()
            .subscribe().withSubscriber(AssertSubscriber.create(10));

        var lateEvent = lifecycleEvent(UUID.randomUUID(), "COMPLETED");
        publisher.onLifecycleEvent(lateEvent);

        subscriber.awaitItems(1, Duration.ofSeconds(1));
        assertThat(subscriber.getItems()).containsExactly(lateEvent);
    }

    private static WorkItemLifecycleEvent lifecycleEvent(UUID workItemId, String eventName) {
        return WorkItemLifecycleEvent.fromWire(
            "io.casehub.work.workitem." + eventName.toLowerCase(),
            "urn:casehub:work", "workitem/" + workItemId,
            workItemId, WorkItemStatus.PENDING, java.time.Instant.now(),
            "actor-1", null, null, null, null, "tenant-1",
            null, null, null, null, null);
    }
}
