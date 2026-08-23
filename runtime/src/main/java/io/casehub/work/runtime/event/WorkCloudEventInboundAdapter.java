package io.casehub.work.runtime.event;

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

import java.nio.charset.StandardCharsets;

@ApplicationScoped
public class WorkCloudEventInboundAdapter {

    private static final Logger LOG = Logger.getLogger(WorkCloudEventInboundAdapter.class);

    private final WorkItemTemplateService                     templateService;
    private final WorkItemService                             workItemService;
    private final TenantContextRunner                         tenantContextRunner;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;


    @Inject
    public WorkCloudEventInboundAdapter(final WorkItemTemplateService templateService,
                                        final WorkItemService workItemService,
                                        final TenantContextRunner tenantContextRunner,
                                        final com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.templateService     = templateService;
        this.workItemService     = workItemService;
        this.tenantContextRunner = tenantContextRunner;
        this.objectMapper        = objectMapper;
    }

    private static WorkItemCreateRequest buildRequestFromJson(final com.fasterxml.jackson.databind.JsonNode root,
                                                              final CloudEvent ce,
                                                              final String callerRef) {
        final WorkItemCreateRequest.Builder b = WorkItemCreateRequest.builder();

        ifText(root, "title", b::title);
        ifText(root, "description", b::description);
        ifText(root, "formKey", b::formKey);
        ifText(root, "assigneeId", b::assigneeId);
        ifText(root, "candidateGroups", b::candidateGroups);
        ifText(root, "candidateUsers", b::candidateUsers);
        ifText(root, "requiredCapabilities", b::requiredCapabilities);
        ifText(root, "payload", b::payload);
        ifText(root, "scope", b::scope);
        ifText(root, "payloadTypeName", b::payloadTypeName);
        ifText(root, "resolutionTypeName", b::resolutionTypeName);
        ifText(root, "candidateScores", b::candidateScores);
        ifText(root, "routingExperiences", b::routingExperiences);
        ifText(root, "routingStrategy", b::routingStrategy);
        ifText(root, "excludedUsers", b::excludedUsers);
        ifText(root, "inputDataSchema", b::inputDataSchema);
        ifText(root, "outputDataSchema", b::outputDataSchema);

        if (root.has("priority") && !root.get("priority").isNull()) {
            b.priority(io.casehub.work.api.WorkItemPriority.valueOf(root.get("priority").asText()));
        }
        if (root.has("templateId") && !root.get("templateId").isNull()) {
            b.templateId(java.util.UUID.fromString(root.get("templateId").asText()));
        }
        if (root.has("expiresAt") && !root.get("expiresAt").isNull()) {
            b.expiresAt(java.time.Instant.parse(root.get("expiresAt").asText()));
        }
        if (root.has("claimDeadline") && !root.get("claimDeadline").isNull()) {
            b.claimDeadline(java.time.Instant.parse(root.get("claimDeadline").asText()));
        }
        if (root.has("followUpDate") && !root.get("followUpDate").isNull()) {
            b.followUpDate(java.time.Instant.parse(root.get("followUpDate").asText()));
        }
        if (root.has("claimDeadlineBusinessHours") && !root.get("claimDeadlineBusinessHours").isNull()) {
            b.claimDeadlineBusinessHours(root.get("claimDeadlineBusinessHours").intValue());
        }
        if (root.has("expiresAtBusinessHours") && !root.get("expiresAtBusinessHours").isNull()) {
            b.expiresAtBusinessHours(root.get("expiresAtBusinessHours").intValue());
        }
        if (root.has("minimumScore") && !root.get("minimumScore").isNull()) {
            b.minimumScore(root.get("minimumScore").doubleValue());
        }
        if (root.has("confidenceScore") && !root.get("confidenceScore").isNull()) {
            b.confidenceScore(root.get("confidenceScore").doubleValue());
        }
        if (root.has("types") && root.get("types").isArray()) {
            final java.util.List<String> types = new java.util.ArrayList<>();
            root.get("types").forEach(n -> types.add(n.asText()));
            b.types(types);
        }
        if (root.has("permittedOutcomes") && root.get("permittedOutcomes").isArray()) {
            final java.util.List<io.casehub.work.api.Outcome> outcomes = new java.util.ArrayList<>();
            for (final com.fasterxml.jackson.databind.JsonNode n : root.get("permittedOutcomes")) {
                outcomes.add(new io.casehub.work.api.Outcome(
                        n.has("name") ? n.get("name").asText() : null,
                        n.has("displayName") ? n.get("displayName").asText() : null,
                        n.has("condition") ? n.get("condition").asText() : null));
            }
            b.permittedOutcomes(outcomes);
        }
        if (root.has("labels") && root.get("labels").isArray()) {
            final java.util.List<io.casehub.work.api.WorkItemLabelRequest> labels = new java.util.ArrayList<>();
            for (final com.fasterxml.jackson.databind.JsonNode n : root.get("labels")) {
                final io.casehub.work.api.LabelPersistence persistence = n.has("persistence") && !n.get("persistence").isNull()
                                                                         ? io.casehub.work.api.LabelPersistence.valueOf(n.get("persistence").asText())
                                                                         : io.casehub.work.api.LabelPersistence.MANUAL;
                labels.add(new io.casehub.work.api.WorkItemLabelRequest(
                        n.has("path") ? n.get("path").asText() : null,
                        persistence,
                        n.has("appliedBy") ? n.get("appliedBy").asText() : null));
            }
            b.labels(labels);
        }

        b.createdBy("cloudevent:" + ce.getSource());
        b.callerRef(callerRef);
        b.tenancyId(null);

        return b.build();
    }

    private static void ifText(final com.fasterxml.jackson.databind.JsonNode root,
                               final String field,
                               final java.util.function.Consumer<String> setter) {
        if (root.has(field) && !root.get(field).isNull()) {
            setter.accept(root.get(field).asText());
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

    public void onCloudEvent(@ObservesAsync final CloudEvent ce) {
        if (!WorkCloudEventTypes.REQUESTED.equals(ce.getType())) {
            return;
        }

        final Object tenancyIdExt  = ce.getExtension(WorkCloudEventTypes.EXT_TENANCY_ID);
        final Object templateIdExt = ce.getExtension(WorkCloudEventTypes.EXT_TEMPLATE_ID);

        if (tenancyIdExt == null) {
            LOG.errorf("CloudEvent %s from %s rejected: missing tenancyid extension", ce.getId(), ce.getSource());
            return;
        }
        if (templateIdExt == null) {
            LOG.errorf("CloudEvent %s from %s rejected: missing templateid extension", ce.getId(), ce.getSource());
            return;
        }

        final String tenancyId   = tenancyIdExt.toString();
        final String templateRef = templateIdExt.toString();

        tenantContextRunner.runInTenantContext(tenancyId, () -> processInTenantContext(ce, templateRef));
    }

    public void onCreateCloudEvent(@ObservesAsync final CloudEvent ce) {
        if (!WorkCloudEventTypes.CREATE.equals(ce.getType())) {
            return;
        }

        final Object tenancyIdExt = ce.getExtension(WorkCloudEventTypes.EXT_TENANCY_ID);
        if (tenancyIdExt == null) {
            LOG.errorf("CloudEvent %s from %s rejected: missing tenancyid extension", ce.getId(), ce.getSource());
            return;
        }

        final String tenancyId = tenancyIdExt.toString();
        tenantContextRunner.runInTenantContext(tenancyId, () -> processCreateInTenantContext(ce));
    }

    private void processCreateInTenantContext(final CloudEvent ce) {
        final com.fasterxml.jackson.databind.JsonNode root;
        try {
            if (ce.getData() == null) {
                LOG.errorf("CloudEvent %s from %s rejected: null data", ce.getId(), ce.getSource());
                return;
            }
            root = objectMapper.readTree(ce.getData().toBytes());
        } catch (final Exception e) {
            LOG.errorf("CloudEvent %s from %s rejected: malformed data — %s", ce.getId(), ce.getSource(), e.getMessage());
            return;
        }

        final String callerRef = root.has("callerRef") && !root.get("callerRef").isNull()
                                 ? root.get("callerRef").asText()
                                 : ce.getId();

        if (workItemService.findByCallerRef(callerRef).isPresent()) {
            LOG.debugf("CloudEvent %s already processed (callerRef=%s) — skipping", ce.getId(), callerRef);
            return;
        }

        final WorkItemCreateRequest request;
        try {
            request = buildRequestFromJson(root, ce, callerRef);
        } catch (final IllegalArgumentException e) {
            LOG.errorf("CloudEvent %s from %s rejected: invalid request — %s", ce.getId(), ce.getSource(), e.getMessage());
            return;
        }

        try {
            if (request.templateId != null) {
                templateService.createFromTemplate(request);
            } else {
                workItemService.create(request);
            }
        } catch (final jakarta.persistence.PersistenceException e) {
            if (isUniqueConstraintViolation(e)) {
                LOG.debugf("CloudEvent %s — concurrent duplicate caught by database constraint", ce.getId());
                return;
            }
            throw e;
        }
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
}
