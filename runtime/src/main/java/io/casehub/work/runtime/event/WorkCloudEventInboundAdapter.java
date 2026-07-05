package io.casehub.work.runtime.event;

import java.nio.charset.StandardCharsets;

import io.casehub.work.api.WorkCloudEventTypes;
import io.casehub.work.api.WorkItemCreateRequest;
import io.casehub.work.runtime.model.WorkItemTemplate;
import io.casehub.work.runtime.service.TenantContextRunner;
import io.casehub.work.runtime.service.WorkItemService;
import io.casehub.work.runtime.service.WorkItemTemplateService;
import io.cloudevents.CloudEvent;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

@ApplicationScoped
public class WorkCloudEventInboundAdapter {

    private static final Logger LOG = Logger.getLogger(WorkCloudEventInboundAdapter.class);

    private final WorkItemTemplateService templateService;
    private final WorkItemService workItemService;
    private final TenantContextRunner tenantContextRunner;

    @Inject
    public WorkCloudEventInboundAdapter(final WorkItemTemplateService templateService,
                                         final WorkItemService workItemService,
                                         final TenantContextRunner tenantContextRunner) {
        this.templateService = templateService;
        this.workItemService = workItemService;
        this.tenantContextRunner = tenantContextRunner;
    }

    public void onCloudEvent(@ObservesAsync final CloudEvent ce) {
        if (!WorkCloudEventTypes.REQUESTED.equals(ce.getType())) {
            return;
        }

        final Object tenancyIdExt = ce.getExtension(WorkCloudEventTypes.EXT_TENANCY_ID);
        final Object templateIdExt = ce.getExtension(WorkCloudEventTypes.EXT_TEMPLATE_ID);

        if (tenancyIdExt == null) {
            LOG.errorf("CloudEvent %s from %s rejected: missing tenancyid extension", ce.getId(), ce.getSource());
            return;
        }
        if (templateIdExt == null) {
            LOG.errorf("CloudEvent %s from %s rejected: missing templateid extension", ce.getId(), ce.getSource());
            return;
        }

        final String tenancyId = tenancyIdExt.toString();
        final String templateRef = templateIdExt.toString();

        tenantContextRunner.runInTenantContext(tenancyId, () -> processInTenantContext(ce, templateRef));
    }

    private void processInTenantContext(final CloudEvent ce, final String templateRef) {
        if (workItemService.findByCallerRef(ce.getId()).isPresent()) {
            LOG.debugf("CloudEvent %s already processed — skipping", ce.getId());
            return;
        }

        final WorkItemTemplate template = templateService.findByRef(templateRef).orElse(null);
        if (template == null) {
            LOG.errorf("CloudEvent %s from %s rejected: template '%s' not found",
                    ce.getId(), ce.getSource(), templateRef);
            return;
        }

        final String payload = ce.getData() != null
                ? new String(ce.getData().toBytes(), StandardCharsets.UTF_8)
                : null;

        final WorkItemCreateRequest request = WorkItemCreateRequest.builder()
                .templateId(template.id)
                .payload(payload)
                .callerRef(ce.getId())
                .createdBy("cloudevent:" + ce.getSource())
                .build();

        try {
            templateService.createFromTemplate(request);
        } catch (final jakarta.persistence.PersistenceException e) {
            if (isUniqueConstraintViolation(e)) {
                LOG.debugf("CloudEvent %s — concurrent duplicate caught by database constraint", ce.getId());
                return;
            }
            throw e;
        }
    }

    private static boolean isUniqueConstraintViolation(final Throwable t) {
        Throwable cause = t;
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
