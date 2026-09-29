package io.casehub.work.rest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.casehub.platform.api.mcp.HandWrittenEndpoint;
import io.casehub.work.api.Outcome;
import io.casehub.work.runtime.model.WorkItemTemplate;
import io.casehub.work.runtime.service.WorkItemTemplateService;
import io.casehub.work.runtime.service.WorkItemTemplateValidationService;

@Path("/workitem-templates")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@HandWrittenEndpoint("JSON Merge Patch requires raw JsonNode — cannot be expressed via @PlatformMutation")
public class WorkItemTemplateResource {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    WorkItemTemplateService templateService;

    @PATCH
    @Path("/{id}")
    @Consumes("application/merge-patch+json")
    @Transactional
    public Response patchTemplate(@PathParam("id") final UUID id, final JsonNode patch) {
        if (patch == null || !patch.isObject()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "patch body must be a JSON object")).build();
        }

        final WorkItemTemplate t = templateService.findById(id).orElse(null);
        if (t == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Template not found")).build();
        }

        if (patch.has("name")) {
            final JsonNode nameNode = patch.get("name");
            if (nameNode.isNull()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "name is required when provided in a PATCH")).build();
            }
            final String newName = nameNode.asText();
            if (!newName.equals(t.name) && templateService.findByName(newName).isPresent()) {
                return Response.status(Response.Status.CONFLICT)
                        .entity(Map.of("error", "template with name '" + newName + "' already exists")).build();
            }
            t.name = newName;
        }

        if (patch.has("description"))          t.description = textOrNull(patch, "description");
        if (patch.has("typePaths"))            t.typePaths = textOrNull(patch, "typePaths");
        if (patch.has("candidateGroups"))      t.candidateGroups = textOrNull(patch, "candidateGroups");
        if (patch.has("candidateUsers"))       t.candidateUsers = textOrNull(patch, "candidateUsers");
        if (patch.has("requiredCapabilities")) t.requiredCapabilities = textOrNull(patch, "requiredCapabilities");
        if (patch.has("defaultPayload"))       t.defaultPayload = textOrNull(patch, "defaultPayload");
        if (patch.has("labelPaths"))           t.labelPaths = textOrNull(patch, "labelPaths");
        if (patch.has("parentRole"))           t.parentRole = textOrNull(patch, "parentRole");
        if (patch.has("assignmentStrategy"))   t.assignmentStrategy = textOrNull(patch, "assignmentStrategy");
        if (patch.has("onThresholdReached"))   t.onThresholdReached = textOrNull(patch, "onThresholdReached");
        if (patch.has("excludedUsers"))        t.excludedUsers = textOrNull(patch, "excludedUsers");
        if (patch.has("excludedGroups"))       t.excludedGroups = textOrNull(patch, "excludedGroups");
        if (patch.has("scope"))                t.scope = textOrNull(patch, "scope");

        if (patch.has("defaultExpiryHours"))
            t.defaultExpiryHours = patch.get("defaultExpiryHours").isNull() ? null : patch.get("defaultExpiryHours").intValue();
        if (patch.has("defaultClaimHours"))
            t.defaultClaimHours = patch.get("defaultClaimHours").isNull() ? null : patch.get("defaultClaimHours").intValue();
        if (patch.has("defaultExpiryBusinessHours"))
            t.defaultExpiryBusinessHours = patch.get("defaultExpiryBusinessHours").isNull() ? null : patch.get("defaultExpiryBusinessHours").intValue();
        if (patch.has("defaultClaimBusinessHours"))
            t.defaultClaimBusinessHours = patch.get("defaultClaimBusinessHours").isNull() ? null : patch.get("defaultClaimBusinessHours").intValue();
        if (patch.has("instanceCount"))
            t.instanceCount = patch.get("instanceCount").isNull() ? null : patch.get("instanceCount").intValue();
        if (patch.has("requiredCount"))
            t.requiredCount = patch.get("requiredCount").isNull() ? null : patch.get("requiredCount").intValue();

        if (patch.has("allowSameAssignee"))
            t.allowSameAssignee = patch.get("allowSameAssignee").isNull() ? null : patch.get("allowSameAssignee").booleanValue();

        if (patch.has("priority")) {
            final JsonNode priorityNode = patch.get("priority");
            if (priorityNode.isNull()) {
                t.priority = null;
            } else {
                try {
                    t.priority = io.casehub.work.api.WorkItemPriority.valueOf(priorityNode.asText());
                } catch (final IllegalArgumentException e) {
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(Map.of("error", "invalid priority value: " + priorityNode.asText())).build();
                }
            }
        }

        if (patch.has("outcomes")) {
            final JsonNode outcomesNode = patch.get("outcomes");
            if (outcomesNode.isNull()) {
                t.outcomes = null;
            } else {
                try {
                    final List<Outcome> outcomes = MAPPER.convertValue(
                            outcomesNode, new TypeReference<List<Outcome>>() {});
                    t.outcomes = WorkItemTemplateService.encodeOutcomes(outcomes);
                } catch (final Exception e) {
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(Map.of("error", "invalid outcomes: " + e.getMessage())).build();
                }
            }
        }

        if (patch.has("inputDataSchema")) {
            final JsonNode schemaNode = patch.get("inputDataSchema");
            if (schemaNode.isNull()) {
                t.inputDataSchema = null;
            } else if (!schemaNode.isObject()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "inputDataSchema must be a JSON object, not a "
                                + schemaNode.getNodeType().name().toLowerCase())).build();
            } else {
                t.inputDataSchema = schemaNode.toString();
            }
        }

        if (patch.has("outputDataSchema")) {
            final JsonNode schemaNode = patch.get("outputDataSchema");
            if (schemaNode.isNull()) {
                t.outputDataSchema = null;
            } else if (!schemaNode.isObject()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "outputDataSchema must be a JSON object, not a "
                                + schemaNode.getNodeType().name().toLowerCase())).build();
            } else {
                t.outputDataSchema = schemaNode.toString();
            }
        }

        try {
            WorkItemTemplateValidationService.validate(t);
        } catch (final IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage())).build();
        }
        t.version++;

        return Response.ok(toResponse(t)).build();
    }

    private static String textOrNull(final JsonNode patch, final String field) {
        final JsonNode node = patch.get(field);
        return (node == null || node.isNull()) ? null : node.asText();
    }

    private Map<String, Object> toResponse(final WorkItemTemplate t) {
        final LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.id);
        m.put("version", t.version);
        m.put("name", t.name);
        m.put("description", t.description);
        m.put("typePaths", t.typePaths);
        m.put("priority", t.priority != null ? t.priority.name() : null);
        m.put("candidateGroups", t.candidateGroups);
        m.put("candidateUsers", t.candidateUsers);
        m.put("requiredCapabilities", t.requiredCapabilities);
        m.put("defaultExpiryHours", t.defaultExpiryHours);
        m.put("defaultClaimHours", t.defaultClaimHours);
        m.put("defaultExpiryBusinessHours", t.defaultExpiryBusinessHours);
        m.put("defaultClaimBusinessHours", t.defaultClaimBusinessHours);
        m.put("defaultPayload", t.defaultPayload);
        m.put("labelPaths", t.labelPaths);
        m.put("instanceCount", t.instanceCount);
        m.put("requiredCount", t.requiredCount);
        m.put("parentRole", t.parentRole);
        m.put("assignmentStrategy", t.assignmentStrategy);
        m.put("onThresholdReached", t.onThresholdReached);
        m.put("allowSameAssignee", t.allowSameAssignee);
        m.put("outcomes", t.outcomes == null ? null : WorkItemTemplateService.decodeOutcomes(t.outcomes));
        m.put("inputDataSchema", t.inputDataSchema);
        m.put("outputDataSchema", t.outputDataSchema);
        m.put("excludedUsers", t.excludedUsers);
        m.put("excludedGroups", t.excludedGroups);
        m.put("scope", t.scope);
        m.put("createdBy", t.createdBy);
        m.put("createdAt", t.createdAt);
        return m;
    }
}
