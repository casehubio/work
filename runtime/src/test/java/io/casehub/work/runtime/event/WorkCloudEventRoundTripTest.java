package io.casehub.work.runtime.event;

import io.casehub.work.api.WorkCloudEventTypes;
import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemTemplate;
import io.casehub.work.runtime.repository.WorkItemStore;
import io.casehub.work.runtime.repository.WorkItemTemplateStore;
import io.casehub.work.runtime.service.WorkItemService;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying the full CloudEvent → WorkItem → CloudEvent round trip.
 *
 * Tests that:
 * - A REQUESTED CloudEvent creates a WorkItem via WorkCloudEventInboundAdapter
 * - The WorkItem is created with template fields applied
 * - Completing the WorkItem emits a COMPLETED CloudEvent via WorkCloudEventAdapter
 * - Duplicate CloudEvents are rejected (idempotency via callerRef)
 */
@QuarkusTest
class WorkCloudEventRoundTripTest {

    @Inject TestCloudEventCapture cloudEventCapture;
    @Inject WorkItemTemplateStore templateStore;
    @Inject WorkItemService workItemService;
    @Inject WorkItemStore workItemStore;
    @Inject Event<CloudEvent> cloudEventBus;

    private UUID templateId;

    @BeforeEach
    @Transactional
    void setUp() {
        cloudEventCapture.clear();
        WorkItemTemplate.deleteAll();

        final WorkItemTemplate template = new WorkItemTemplate();
        template.id = UUID.randomUUID();
        template.name = "Test Template " + UUID.randomUUID();
        template.typePaths = "[\"test-type\"]";
        template.priority = WorkItemPriority.HIGH;
        template.candidateGroups = "test-group";
        template.defaultExpiryHours = 48;
        template.createdBy = "test-user";

        templateStore.put(template);
        templateId = template.id;
    }

    @Test
    @Transactional
    void roundTrip_cloudEventToWorkItemToCloudEvent() throws InterruptedException {
        final String ceId = UUID.randomUUID().toString();
        final String source = "/workflows/test-approval";
        final String payload = "{\"amount\":500,\"currency\":\"USD\"}";

        final CloudEvent requestedEvent = CloudEventBuilder.v1()
                .withId(ceId)
                .withType(WorkCloudEventTypes.REQUESTED)
                .withSource(URI.create(source))
                .withDataContentType("application/json")
                .withData(payload.getBytes(StandardCharsets.UTF_8))
                .withExtension(WorkCloudEventTypes.EXT_TENANCY_ID, "default")
                .withExtension(WorkCloudEventTypes.EXT_TEMPLATE_ID, templateId.toString())
                .build();

        cloudEventBus.fireAsync(requestedEvent);

        Thread.sleep(2000);

        final WorkItem workItem = workItemService.findByCallerRef(ceId).orElseThrow();
        assertThat(workItem.callerRef).isEqualTo(ceId);
        assertThat(workItem.payload).isEqualTo(payload);
        assertThat(workItem.createdBy).isEqualTo("cloudevent:" + source);
        assertThat(workItem.types).extracting(t -> t.path).containsExactly("test-type");
        assertThat(workItem.priority).isEqualTo(WorkItemPriority.HIGH);
        assertThat(workItem.candidateGroups).isEqualTo("test-group");

        workItemService.claim(workItem.id, "test-actor");
        workItemService.start(workItem.id, "test-actor");
        workItemService.complete(workItem.id, "test-actor", null, null);

        Thread.sleep(2000);

        final List<CloudEvent> completedEvents = cloudEventCapture.ofType(WorkCloudEventTypes.COMPLETED);
        assertThat(completedEvents).hasSize(1);
        final CloudEvent completedEvent = completedEvents.get(0);
        assertThat(completedEvent.getId()).isNotNull();
        assertThat(completedEvent.getType()).isEqualTo(WorkCloudEventTypes.COMPLETED);
    }

    @Test
    @Transactional
    void idempotency_duplicateCloudEvent_doesNotCreateDuplicateWorkItem() throws InterruptedException {
        final String ceId = UUID.randomUUID().toString();
        final String source = "/workflows/duplicate-test";
        final String payload = "{\"test\":\"data\"}";

        final CloudEvent event = CloudEventBuilder.v1()
                .withId(ceId)
                .withType(WorkCloudEventTypes.REQUESTED)
                .withSource(URI.create(source))
                .withDataContentType("application/json")
                .withData(payload.getBytes(StandardCharsets.UTF_8))
                .withExtension(WorkCloudEventTypes.EXT_TENANCY_ID, "default")
                .withExtension(WorkCloudEventTypes.EXT_TEMPLATE_ID, templateId.toString())
                .build();

        cloudEventBus.fireAsync(event);

        Thread.sleep(2000);

        final long countAfterFirst = workItemStore.scanAll().stream()
                .filter(wi -> ceId.equals(wi.callerRef))
                .count();
        assertThat(countAfterFirst).isEqualTo(1);

        cloudEventBus.fireAsync(event);

        Thread.sleep(1000);

        final long countAfterSecond = workItemStore.scanAll().stream()
                .filter(wi -> ceId.equals(wi.callerRef))
                .count();
        assertThat(countAfterSecond).isEqualTo(1);
    }
}
