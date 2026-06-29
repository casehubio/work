package io.casehub.work.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.work.api.WorkItemCreateRequest;
import io.casehub.work.runtime.model.AuditEntry;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemSpawnGroup;
import io.casehub.work.runtime.model.WorkItemTemplate;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for WorkItemTemplateService.instantiate() callerRef overload.
 * Requires CDI + JPA — see WorkItemTemplateServiceTest for pure-unit mapping tests.
 */
@QuarkusTest
class WorkItemTemplateInstantiateTest {

    @Inject
    WorkItemTemplateService templateService;

    @BeforeEach
    @Transactional
    void cleanup() {
        AuditEntry.deleteAll();
        WorkItemSpawnGroup.deleteAll();
        WorkItem.deleteAll();
        WorkItemTemplate.deleteAll();
    }

    @Test
    void instantiate_setsCallerRef_onCreatedWorkItem() {
        final WorkItemTemplate template = persistedTemplate("IRB Review", null);
        final String callerRef = "case:550e8400-e29b-41d4-a716-446655440000/pi:irb-gate";

        final var request = WorkItemCreateRequest.builder()
                .templateId(template.id)
                .createdBy("system:engine")
                .callerRef(callerRef)
                .build();
        final WorkItem workItem = templateService.createFromTemplate(request);

        assertThat(workItem.callerRef).isEqualTo(callerRef);
    }

    @Test
    void instantiate_multiInstanceTemplate_setsCallerRefOnParent() {
        final WorkItemTemplate template = persistedTemplate("Parallel Review", 3);
        final String callerRef = "case:550e8400-e29b-41d4-a716-446655440000/pi:review-gate";

        final var request = WorkItemCreateRequest.builder()
                .templateId(template.id)
                .createdBy("system:engine")
                .callerRef(callerRef)
                .build();
        final WorkItem parent = templateService.createFromTemplate(request);

        assertThat(parent).isNotNull();
        assertThat(parent.callerRef).isEqualTo(callerRef);
    }

    @Test
    void instantiate_4arg_callerRefIsNull() {
        final WorkItemTemplate template = persistedTemplate("Human Task", null);

        final var request = WorkItemCreateRequest.builder()
                .templateId(template.id)
                .createdBy("system")
                .build();
        final WorkItem workItem = templateService.createFromTemplate(request);

        assertThat(workItem.callerRef).isNull();
    }

    @Transactional
    WorkItemTemplate persistedTemplate(final String name, final Integer instanceCount) {
        final WorkItemTemplate t = new WorkItemTemplate();
        t.name = name;
        t.candidateGroups = "reviewers";
        t.createdBy = "admin";
        t.instanceCount = instanceCount;
        t.tenancyId = io.casehub.platform.api.identity.TenancyConstants.DEFAULT_TENANT_ID;
        t.persist();
        return t;
    }
}
