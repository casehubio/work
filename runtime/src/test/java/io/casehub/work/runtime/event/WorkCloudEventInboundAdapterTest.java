package io.casehub.work.runtime.event;

import io.casehub.work.api.WorkCloudEventTypes;
import io.casehub.work.api.WorkItem;
import io.casehub.work.api.WorkItemCreateRequest;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WorkCloudEventInboundAdapterTest {

    private WorkItemTemplateService templateService;
    private WorkItemService workItemService;
    private TenantContextRunner tenantContextRunner;
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private WorkCloudEventInboundAdapter adapter;

    @BeforeEach
    void setUp() {
        templateService = mock(WorkItemTemplateService.class);
        workItemService = mock(WorkItemService.class);
        tenantContextRunner = mock(TenantContextRunner.class);
        objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(1)).run();
            return null;
        })
                .when(tenantContextRunner).runInTenantContext(any(), any(Runnable.class));
        adapter = new WorkCloudEventInboundAdapter(templateService, workItemService, tenantContextRunner, objectMapper);
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

        final WorkItem created = WorkItem.builder().id(UUID.randomUUID()).build();
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
        final String         ceId     = UUID.randomUUID().toString();
        final WorkItem existing = WorkItem.builder().id(UUID.randomUUID()).build();
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

// --- CREATE path helpers ---


// --- CREATE path tests ---

    @Test
    void onCreateCloudEvent_inlinePath_createsWorkItem() {
        final String tenancy   = "tenant-1";
        final String ceId      = UUID.randomUUID().toString();
        final String callerRef = "case:abc-123/pi:def-456";
        final String data = "{\"title\":\"Review document\",\"candidateGroups\":\"legal,compliance\","
                            + "\"callerRef\":\"" + callerRef + "\",\"payload\":\"{\\\"docId\\\":\\\"d1\\\"}\","
                            + "\"scope\":\"app/legal\",\"candidateScores\":\"{\\\"alice\\\":0.95}\"}";

        when(workItemService.findByCallerRef(callerRef)).thenReturn(Optional.empty());
        final WorkItem created = WorkItem.builder().id(UUID.randomUUID()).build();
        when(workItemService.create(any())).thenReturn(created);

        adapter.onCreateCloudEvent(buildCreateEvent(ceId, data, tenancy));

        verify(tenantContextRunner).runInTenantContext(eq(tenancy), any(Runnable.class));

        final ArgumentCaptor<WorkItemCreateRequest> captor = ArgumentCaptor.forClass(WorkItemCreateRequest.class);
        verify(workItemService).create(captor.capture());

        final WorkItemCreateRequest req = captor.getValue();
        assertThat(req.title).isEqualTo("Review document");
        assertThat(req.candidateGroups).isEqualTo("legal,compliance");
        assertThat(req.callerRef).isEqualTo(callerRef);
        assertThat(req.createdBy).isEqualTo("cloudevent:/engine/cases/abc-123");
        assertThat(req.payload).isEqualTo("{\"docId\":\"d1\"}");
        assertThat(req.scope).isEqualTo("app/legal");
        assertThat(req.candidateScores).isEqualTo("{\"alice\":0.95}");
        assertThat(req.tenancyId).isNull();
    }

    @Test
    void onCreateCloudEvent_templatePath_createsViaTemplate() {
        final UUID   templateId = UUID.randomUUID();
        final String callerRef  = "workflow:run-1/step-3";
        final String data       = "{\"templateId\":\"" + templateId + "\",\"payload\":\"{\\\"doc\\\":\\\"d1\\\"}\",\"callerRef\":\"" + callerRef + "\"}";

        when(workItemService.findByCallerRef(callerRef)).thenReturn(Optional.empty());
        final WorkItemTemplate template = new WorkItemTemplate();
        template.id = templateId;
        when(templateService.findByRef(templateId.toString())).thenReturn(Optional.of(template));
        final WorkItem created = WorkItem.builder().id(UUID.randomUUID()).build();
        when(templateService.createFromTemplate(any())).thenReturn(created);

        adapter.onCreateCloudEvent(buildCreateEvent(data, "tenant-1"));

        final ArgumentCaptor<WorkItemCreateRequest> captor = ArgumentCaptor.forClass(WorkItemCreateRequest.class);
        verify(templateService).createFromTemplate(captor.capture());
        assertThat(captor.getValue().templateId).isEqualTo(templateId);
        assertThat(captor.getValue().callerRef).isEqualTo(callerRef);
        verify(workItemService, never()).create(any());
    }

    @Test
    void onCreateCloudEvent_missingTenancyId_rejects() {
        final CloudEvent ce = CloudEventBuilder.v1()
                                               .withId(UUID.randomUUID().toString())
                                               .withType(WorkCloudEventTypes.CREATE)
                                               .withSource(URI.create("/test"))
                                               .withData("application/json", "{\"title\":\"t\"}".getBytes(StandardCharsets.UTF_8))
                                               .build();

        adapter.onCreateCloudEvent(ce);

        verifyNoInteractions(workItemService, templateService);
    }

    @Test
    void onCreateCloudEvent_callerRefFallsBackToCeId() {
        final String ceId = UUID.randomUUID().toString();
        final String data = "{\"title\":\"No callerRef\"}";

        when(workItemService.findByCallerRef(ceId)).thenReturn(Optional.empty());
        final WorkItem created = WorkItem.builder().id(UUID.randomUUID()).build();
        when(workItemService.create(any())).thenReturn(created);

        adapter.onCreateCloudEvent(buildCreateEvent(ceId, data, "tenant-1"));

        final ArgumentCaptor<WorkItemCreateRequest> captor = ArgumentCaptor.forClass(WorkItemCreateRequest.class);
        verify(workItemService).create(captor.capture());
        assertThat(captor.getValue().callerRef).isEqualTo(ceId);
    }

    @Test
    void onCreateCloudEvent_duplicateCallerRef_skips() {
        final String callerRef = "case:abc/pi:def";
        final String data      = "{\"title\":\"t\",\"callerRef\":\"" + callerRef + "\"}";

        when(workItemService.findByCallerRef(callerRef))
                .thenReturn(Optional.of(WorkItem.builder().id(UUID.randomUUID()).build()));

        adapter.onCreateCloudEvent(buildCreateEvent(data, "tenant-1"));

        verify(workItemService, never()).create(any());
        verify(templateService, never()).createFromTemplate(any());
    }

    @Test
    void onCreateCloudEvent_createdByOverridden() {
        final String data = "{\"title\":\"t\",\"createdBy\":\"malicious-source\"}";

        when(workItemService.findByCallerRef(any())).thenReturn(Optional.empty());
        final WorkItem created = WorkItem.builder().id(UUID.randomUUID()).build();
        when(workItemService.create(any())).thenReturn(created);

        adapter.onCreateCloudEvent(buildCreateEvent(data, "tenant-1"));

        final ArgumentCaptor<WorkItemCreateRequest> captor = ArgumentCaptor.forClass(WorkItemCreateRequest.class);
        verify(workItemService).create(captor.capture());
        assertThat(captor.getValue().createdBy).startsWith("cloudevent:");
    }

    @Test
    void onCreateCloudEvent_tenancyIdInDataIgnored() {
        final String data = "{\"title\":\"t\",\"tenancyId\":\"tenant-B\"}";

        when(workItemService.findByCallerRef(any())).thenReturn(Optional.empty());
        final WorkItem created = WorkItem.builder().id(UUID.randomUUID()).build();
        when(workItemService.create(any())).thenReturn(created);

        adapter.onCreateCloudEvent(buildCreateEvent(data, "tenant-A"));

        verify(tenantContextRunner).runInTenantContext(eq("tenant-A"), any(Runnable.class));

        final ArgumentCaptor<WorkItemCreateRequest> captor = ArgumentCaptor.forClass(WorkItemCreateRequest.class);
        verify(workItemService).create(captor.capture());
        assertThat(captor.getValue().tenancyId).isNull();
    }

    @Test
    void onCreateCloudEvent_nonCreateType_ignored() {
        final CloudEvent ce = CloudEventBuilder.v1()
                                               .withId(UUID.randomUUID().toString())
                                               .withType(WorkCloudEventTypes.COMPLETED)
                                               .withSource(URI.create("/test"))
                                               .build();

        adapter.onCreateCloudEvent(ce);

        verifyNoInteractions(workItemService, templateService);
    }

    @Test
    void onCreateCloudEvent_malformedData_rejects() {
        final CloudEvent ce = CloudEventBuilder.v1()
                                               .withId(UUID.randomUUID().toString())
                                               .withType(WorkCloudEventTypes.CREATE)
                                               .withSource(URI.create("/test"))
                                               .withData("application/json", "not-json".getBytes(StandardCharsets.UTF_8))
                                               .withExtension(WorkCloudEventTypes.EXT_TENANCY_ID, "tenant-1")
                                               .build();

        adapter.onCreateCloudEvent(ce);

        verify(workItemService, never()).create(any());
    }

    @Test
    void onCreateCloudEvent_nullData_rejects() {
        final CloudEvent ce = CloudEventBuilder.v1()
                                               .withId(UUID.randomUUID().toString())
                                               .withType(WorkCloudEventTypes.CREATE)
                                               .withSource(URI.create("/test"))
                                               .withExtension(WorkCloudEventTypes.EXT_TENANCY_ID, "tenant-1")
                                               .build();

        adapter.onCreateCloudEvent(ce);

        verify(workItemService, never()).create(any());
    }

    @Test
    void onCreateCloudEvent_constraintViolation_idempotentSuccess() {
        final String data = "{\"title\":\"t\"}";

        when(workItemService.findByCallerRef(any())).thenReturn(Optional.empty());
        when(workItemService.create(any())).thenThrow(
                new jakarta.persistence.PersistenceException("dup",
                                                             new org.hibernate.exception.ConstraintViolationException("dup", null, "uq_workitem")));

        adapter.onCreateCloudEvent(buildCreateEvent(data, "tenant-1"));

        verify(workItemService).create(any());
    }

    @Test
    void onCreateCloudEvent_permittedOutcomesMapping() {
        final String data = "{\"title\":\"t\",\"permittedOutcomes\":[{\"name\":\"approve\",\"displayName\":\"Approve It\",\"condition\":\"workItem.priority == 'HIGH'\"},{\"name\":\"reject\"}]}";

        when(workItemService.findByCallerRef(any())).thenReturn(Optional.empty());
        final WorkItem created = WorkItem.builder().id(UUID.randomUUID()).build();
        when(workItemService.create(any())).thenReturn(created);

        adapter.onCreateCloudEvent(buildCreateEvent(data, "tenant-1"));

        final ArgumentCaptor<WorkItemCreateRequest> captor = ArgumentCaptor.forClass(WorkItemCreateRequest.class);
        verify(workItemService).create(captor.capture());
        final var outcomes = captor.getValue().permittedOutcomes;
        assertThat(outcomes).hasSize(2);
        assertThat(outcomes.get(0).name()).isEqualTo("approve");
        assertThat(outcomes.get(0).displayName()).isEqualTo("Approve It");
        assertThat(outcomes.get(0).condition()).isEqualTo("workItem.priority == 'HIGH'");
        assertThat(outcomes.get(1).name()).isEqualTo("reject");
        assertThat(outcomes.get(1).displayName()).isNull();
    }

    @Test
    void onCreateCloudEvent_labelsMapping() {
        final String data = "{\"title\":\"t\",\"labels\":[{\"path\":\"priority/high\",\"persistence\":\"MANUAL\",\"appliedBy\":\"system\"},{\"path\":\"auto/flagged\"}]}";

        when(workItemService.findByCallerRef(any())).thenReturn(Optional.empty());
        final WorkItem created = WorkItem.builder().id(UUID.randomUUID()).build();
        when(workItemService.create(any())).thenReturn(created);

        adapter.onCreateCloudEvent(buildCreateEvent(data, "tenant-1"));

        final ArgumentCaptor<WorkItemCreateRequest> captor = ArgumentCaptor.forClass(WorkItemCreateRequest.class);
        verify(workItemService).create(captor.capture());
        final var labels = captor.getValue().labels;
        assertThat(labels).hasSize(2);
        assertThat(labels.get(0).path()).isEqualTo("priority/high");
        assertThat(labels.get(0).persistence()).isEqualTo(io.casehub.work.api.LabelPersistence.MANUAL);
        assertThat(labels.get(0).appliedBy()).isEqualTo("system");
        assertThat(labels.get(1).path()).isEqualTo("auto/flagged");
        assertThat(labels.get(1).persistence()).isEqualTo(io.casehub.work.api.LabelPersistence.MANUAL);
    }


    private CloudEvent buildCreateEvent(final String data, final String tenancy) {
        return buildCreateEvent(UUID.randomUUID().toString(), data, tenancy);
    }

    private CloudEvent buildCreateEvent(final String ceId, final String data, final String tenancy) {
        return CloudEventBuilder.v1()
                                .withId(ceId)
                                .withType(WorkCloudEventTypes.CREATE)
                                .withSource(URI.create("/engine/cases/abc-123"))
                                .withDataContentType("application/json")
                                .withData(data.getBytes(StandardCharsets.UTF_8))
                                .withExtension(WorkCloudEventTypes.EXT_TENANCY_ID, tenancy)
                                .build();
    }
}
