package io.casehub.work.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.work.api.WorkItemLifecycleEvent;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.graphql.dto.WorkItemLifecycleEventType;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkItemSubscriptionResolverTest {

    private WorkItemEventPublisher publisher;
    private WorkItemSubscriptionResolver resolver;

    @BeforeEach
    void setUp() {
        publisher = new WorkItemEventPublisher();
        resolver = new WorkItemSubscriptionResolver(publisher);
    }

    @Test
    void workItemLifecycle_filters_by_id() {
        UUID target = UUID.randomUUID();
        UUID other = UUID.randomUUID();

        var subscriber = resolver.workItemLifecycle(target)
            .subscribe().withSubscriber(AssertSubscriber.create(10));

        publisher.onLifecycleEvent(lifecycleEvent(other, "CREATED", null));
        publisher.onLifecycleEvent(lifecycleEvent(target, "ASSIGNED", "user-1"));
        publisher.onLifecycleEvent(lifecycleEvent(other, "COMPLETED", null));

        subscriber.awaitItems(1, Duration.ofSeconds(1));
        assertThat(subscriber.getItems()).hasSize(1);
        assertThat(subscriber.getItems().get(0).workItemId()).isEqualTo(target);
    }

    @Test
    void workItemLifecycle_maps_fields() {
        UUID id = UUID.randomUUID();

        var subscriber = resolver.workItemLifecycle(id)
            .subscribe().withSubscriber(AssertSubscriber.create(10));

        publisher.onLifecycleEvent(lifecycleEvent(id, "ASSIGNED", "user-1"));

        subscriber.awaitItems(1, Duration.ofSeconds(1));
        WorkItemLifecycleEventType mapped = subscriber.getItems().get(0);
        assertThat(mapped.workItemId()).isEqualTo(id);
        assertThat(mapped.type()).contains("assigned");
        assertThat(mapped.assigneeId()).isEqualTo("user-1");
        assertThat(mapped.tenancyId()).isEqualTo("tenant-1");
    }

    @Test
    void workItemInboxUpdates_filters_by_assignee() {
        UUID wi1 = UUID.randomUUID();
        UUID wi2 = UUID.randomUUID();

        var subscriber = resolver.workItemInboxUpdates("user-1")
            .subscribe().withSubscriber(AssertSubscriber.create(10));

        publisher.onLifecycleEvent(lifecycleEvent(wi1, "ASSIGNED", "user-2"));
        publisher.onLifecycleEvent(lifecycleEvent(wi2, "ASSIGNED", "user-1"));

        subscriber.awaitItems(1, Duration.ofSeconds(1));
        assertThat(subscriber.getItems()).hasSize(1);
        assertThat(subscriber.getItems().get(0).workItemId()).isEqualTo(wi2);
    }

    @Test
    void multiple_subscribers_for_same_item_each_receive() {
        UUID id = UUID.randomUUID();

        var sub1 = resolver.workItemLifecycle(id)
            .subscribe().withSubscriber(AssertSubscriber.create(10));
        var sub2 = resolver.workItemLifecycle(id)
            .subscribe().withSubscriber(AssertSubscriber.create(10));

        publisher.onLifecycleEvent(lifecycleEvent(id, "STARTED", "user-1"));

        sub1.awaitItems(1, Duration.ofSeconds(1));
        sub2.awaitItems(1, Duration.ofSeconds(1));
        assertThat(sub1.getItems()).hasSize(1);
        assertThat(sub2.getItems()).hasSize(1);
    }

    private static WorkItemLifecycleEvent lifecycleEvent(UUID workItemId, String eventName, String assigneeId) {
        return WorkItemLifecycleEvent.fromWire(
            "io.casehub.work.workitem." + eventName.toLowerCase(),
            "urn:casehub:work", "workitem/" + workItemId,
            workItemId, WorkItemStatus.PENDING, Instant.now(),
            "actor-1", null, null, null, null, "tenant-1",
            null, assigneeId, null, null, null);
    }
}
