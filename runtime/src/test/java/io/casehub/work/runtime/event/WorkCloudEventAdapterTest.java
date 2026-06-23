package io.casehub.work.runtime.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.event.Event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.cloudevents.CloudEvent;

import io.casehub.work.api.GroupStatus;
import io.casehub.work.api.WorkItemGroupLifecycleEvent;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemStatus;

@ExtendWith(MockitoExtension.class)
class WorkCloudEventAdapterTest {

    @Mock
    Event<CloudEvent> cloudEventBus;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    private WorkCloudEventAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new WorkCloudEventAdapter(cloudEventBus, objectMapper);
        when(cloudEventBus.fireAsync(any())).thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void workItemEvent_buildsCorrectCloudEventFields() {
        final UUID id = UUID.randomUUID();
        final WorkItem wi = workItem(id, WorkItemStatus.COMPLETED, "tenant-1");
        final WorkItemLifecycleEvent event = WorkItemLifecycleEvent.of("COMPLETED", wi, "alice", "done");

        adapter.onWorkItemLifecycle(event);

        final CloudEvent ce = captureCloudEvent();
        assertThat(ce.getSpecVersion().toString()).isEqualTo("1.0");
        assertThat(ce.getType()).isEqualTo("io.casehub.work.workitem.completed");
        assertThat(ce.getSource().toString()).isEqualTo("/workitems/" + id);
        assertThat(ce.getSubject()).isEqualTo(id.toString());
        assertThat(ce.getDataContentType()).isEqualTo("application/json");
        assertThat(ce.getData().toBytes()).isNotEmpty();
        assertThat(ce.getExtension("tenancyid")).isEqualTo("tenant-1");
        assertThat(ce.getTime()).isNotNull();
    }

    @Test
    void workItemEvent_usesEventTimestampNotAdapterTime() {
        final WorkItem wi = workItem(UUID.randomUUID(), WorkItemStatus.PENDING, "t1");
        final WorkItemLifecycleEvent event = WorkItemLifecycleEvent.of("CREATED", wi, "system", null);
        final Instant eventTime = event.occurredAt();

        adapter.onWorkItemLifecycle(event);

        final CloudEvent ce = captureCloudEvent();
        assertThat(ce.getTime().toInstant()).isEqualTo(eventTime);
    }

    @Test
    void workItemEvent_nullTenancyId_extensionOmitted() {
        final WorkItem wi = workItem(UUID.randomUUID(), WorkItemStatus.PENDING, null);
        final WorkItemLifecycleEvent event = WorkItemLifecycleEvent.of("CREATED", wi, "system", null);

        adapter.onWorkItemLifecycle(event);

        final CloudEvent ce = captureCloudEvent();
        assertThat(ce.getExtension("tenancyid")).isNull();
    }

    @Test
    void workItemEvent_serialisationFailure_firesWithEmptyData() {
        final ObjectMapper brokenMapper = mock(ObjectMapper.class);
        final WorkCloudEventAdapter brokenAdapter = new WorkCloudEventAdapter(cloudEventBus, brokenMapper);
        try {
            when(brokenMapper.writeValueAsBytes(any()))
                    .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("boom") {});
        } catch (final com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        final WorkItem wi = workItem(UUID.randomUUID(), WorkItemStatus.COMPLETED, "t1");
        final WorkItemLifecycleEvent event = WorkItemLifecycleEvent.of("COMPLETED", wi, "alice", null);

        brokenAdapter.onWorkItemLifecycle(event);

        final CloudEvent ce = captureCloudEvent();
        assertThat(ce.getData().toBytes()).isEmpty();
    }

    @Test
    void groupEvent_buildsCorrectCloudEventFields() {
        final UUID parentId = UUID.randomUUID();
        final UUID groupId = UUID.randomUUID();
        final WorkItemGroupLifecycleEvent event = WorkItemGroupLifecycleEvent.of(
                parentId, groupId, 3, 2, 2, 0, GroupStatus.COMPLETED, "caller-ref", "tenant-1");

        adapter.onGroupLifecycle(event);

        final CloudEvent ce = captureCloudEvent();
        assertThat(ce.getSpecVersion().toString()).isEqualTo("1.0");
        assertThat(ce.getType()).isEqualTo("io.casehub.work.group.completed");
        assertThat(ce.getSource().toString()).isEqualTo("/workitems/groups/" + groupId);
        assertThat(ce.getSubject()).isEqualTo(groupId.toString());
        assertThat(ce.getDataContentType()).isEqualTo("application/json");
        assertThat(ce.getData().toBytes()).isNotEmpty();
        assertThat(ce.getExtension("tenancyid")).isEqualTo("tenant-1");
    }

    @Test
    void groupEvent_typeDerivesFromGroupStatus() {
        for (final GroupStatus status : GroupStatus.values()) {
            reset(cloudEventBus);
            when(cloudEventBus.fireAsync(any())).thenReturn(CompletableFuture.completedFuture(null));

            final WorkItemGroupLifecycleEvent event = WorkItemGroupLifecycleEvent.of(
                    UUID.randomUUID(), UUID.randomUUID(), 3, 2, 1, 0, status, null, null);

            adapter.onGroupLifecycle(event);

            final CloudEvent ce = captureCloudEvent();
            assertThat(ce.getType())
                    .isEqualTo("io.casehub.work.group." + status.name().toLowerCase(Locale.ROOT));
        }
    }

    @Test
    void groupEvent_nullTenancyId_extensionOmitted() {
        final WorkItemGroupLifecycleEvent event = WorkItemGroupLifecycleEvent.of(
                UUID.randomUUID(), UUID.randomUUID(), 3, 2, 1, 0,
                GroupStatus.IN_PROGRESS, null, null);

        adapter.onGroupLifecycle(event);

        final CloudEvent ce = captureCloudEvent();
        assertThat(ce.getExtension("tenancyid")).isNull();
    }

    private CloudEvent captureCloudEvent() {
        final ArgumentCaptor<CloudEvent> captor = ArgumentCaptor.forClass(CloudEvent.class);
        verify(cloudEventBus).fireAsync(captor.capture());
        return captor.getValue();
    }

    private WorkItem workItem(final UUID id, final WorkItemStatus status, final String tenancyId) {
        final WorkItem wi = new WorkItem();
        wi.id = id;
        wi.status = status;
        wi.tenancyId = tenancyId;
        return wi;
    }
}
