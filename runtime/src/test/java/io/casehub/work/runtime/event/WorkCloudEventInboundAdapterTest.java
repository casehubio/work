package io.casehub.work.runtime.event;

import io.casehub.work.api.WorkCloudEventTypes;
import io.casehub.work.api.WorkItemCreateRequest;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemTemplate;
import io.casehub.work.runtime.service.TenantContextRunner;
import io.casehub.work.runtime.service.WorkItemService;
import io.casehub.work.runtime.service.WorkItemTemplateService;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class WorkCloudEventInboundAdapterTest {

    private WorkItemTemplateService templateService;
    private WorkItemService workItemService;
    private TenantContextRunner tenantContextRunner;
    private WorkCloudEventInboundAdapter adapter;

    @BeforeEach
    void setUp() {
        templateService = mock(WorkItemTemplateService.class);
        workItemService = mock(WorkItemService.class);
        tenantContextRunner = mock(TenantContextRunner.class);
        doAnswer(inv -> { ((Runnable) inv.getArgument(1)).run(); return null; })
                .when(tenantContextRunner).runInTenantContext(any(), any(Runnable.class));
        adapter = new WorkCloudEventInboundAdapter(templateService, workItemService, tenantContextRunner);
    }

    @Test
    void onCloudEvent_templatePath_createsWorkItem() {
        final UUID templateId = UUID.randomUUID();
        final String tenancy = "tenant-1";
        final String ceId = UUID.randomUUID().toString();
        final String payload = "{\"amount\":500,\"currency\":\"USD\"}";

        final WorkItemTemplate template = new WorkItemTemplate();
        template.id = templateId;
        template.name = "invoice-review";
        when(templateService.findByRef(templateId.toString())).thenReturn(Optional.of(template));

        final WorkItem created = new WorkItem();
        created.id = UUID.randomUUID();
        when(templateService.createFromTemplate(any())).thenReturn(created);
        when(workItemService.findByCallerRef(ceId)).thenReturn(Optional.empty());

        final CloudEvent ce = CloudEventBuilder.v1()
                .withId(ceId)
                .withType(WorkCloudEventTypes.REQUESTED)
                .withSource(URI.create("/workflows/invoice-approval"))
                .withDataContentType("application/json")
                .withData(payload.getBytes(StandardCharsets.UTF_8))
                .withExtension(WorkCloudEventTypes.EXT_TENANCY_ID, tenancy)
                .withExtension(WorkCloudEventTypes.EXT_TEMPLATE_ID, templateId.toString())
                .build();

        adapter.onCloudEvent(ce);

        verify(tenantContextRunner).runInTenantContext(eq(tenancy), any(Runnable.class));

        final ArgumentCaptor<WorkItemCreateRequest> captor = ArgumentCaptor.forClass(WorkItemCreateRequest.class);
        verify(templateService).createFromTemplate(captor.capture());

        final WorkItemCreateRequest req = captor.getValue();
        assertThat(req.templateId).isEqualTo(templateId);
        assertThat(req.payload).isEqualTo(payload);
        assertThat(req.callerRef).isEqualTo(ceId);
        assertThat(req.createdBy).isEqualTo("cloudevent:/workflows/invoice-approval");
    }

    @Test
    void onCloudEvent_ignoresNonRequestedType() {
        final CloudEvent ce = CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withType(WorkCloudEventTypes.COMPLETED)
                .withSource(URI.create("/test"))
                .build();

        adapter.onCloudEvent(ce);

        verifyNoInteractions(templateService, workItemService);
    }

    @Test
    void onCloudEvent_missingTenancyId_rejects() {
        final CloudEvent ce = CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withType(WorkCloudEventTypes.REQUESTED)
                .withSource(URI.create("/test"))
                .withExtension(WorkCloudEventTypes.EXT_TEMPLATE_ID, UUID.randomUUID().toString())
                .build();

        adapter.onCloudEvent(ce);

        verifyNoInteractions(workItemService, templateService);
    }

    @Test
    void onCloudEvent_missingTemplateId_rejects() {
        final CloudEvent ce = CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withType(WorkCloudEventTypes.REQUESTED)
                .withSource(URI.create("/test"))
                .withExtension(WorkCloudEventTypes.EXT_TENANCY_ID, "tenant-1")
                .build();

        adapter.onCloudEvent(ce);

        verifyNoInteractions(workItemService, templateService);
    }

    @Test
    void onCloudEvent_duplicateCallerRef_skips() {
        final String ceId = UUID.randomUUID().toString();
        final WorkItem existing = new WorkItem();
        existing.id = UUID.randomUUID();
        when(workItemService.findByCallerRef(ceId)).thenReturn(Optional.of(existing));

        final CloudEvent ce = CloudEventBuilder.v1()
                .withId(ceId)
                .withType(WorkCloudEventTypes.REQUESTED)
                .withSource(URI.create("/test"))
                .withExtension(WorkCloudEventTypes.EXT_TENANCY_ID, "tenant-1")
                .withExtension(WorkCloudEventTypes.EXT_TEMPLATE_ID, UUID.randomUUID().toString())
                .build();

        adapter.onCloudEvent(ce);

        verify(templateService, never()).createFromTemplate(any());
    }

    @Test
    void onCloudEvent_templateNotFound_rejects() {
        final String ceId = UUID.randomUUID().toString();
        final String templateRef = UUID.randomUUID().toString();
        when(workItemService.findByCallerRef(ceId)).thenReturn(Optional.empty());
        when(templateService.findByRef(templateRef)).thenReturn(Optional.empty());

        final CloudEvent ce = CloudEventBuilder.v1()
                .withId(ceId)
                .withType(WorkCloudEventTypes.REQUESTED)
                .withSource(URI.create("/test"))
                .withExtension(WorkCloudEventTypes.EXT_TENANCY_ID, "tenant-1")
                .withExtension(WorkCloudEventTypes.EXT_TEMPLATE_ID, templateRef)
                .build();

        adapter.onCloudEvent(ce);

        verify(templateService, never()).createFromTemplate(any());
    }
}
