package io.casehub.work.rest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.casehub.platform.api.expression.ExpressionEngineRegistry;
import io.casehub.platform.api.label.LabelAction;
import io.casehub.platform.api.label.LabelRule;
import io.casehub.work.runtime.filter.LabelRuleEntity;
import io.casehub.work.runtime.repository.LabelRuleStore;

@Path("/label-rules")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class LabelRuleResource {

    @Inject
    LabelRuleStore labelRuleStore;

    @Inject
    ExpressionEngineRegistry expressionRegistry;

    @Inject
    Instance<LabelRule> permanentRules;

    @Inject
    io.casehub.work.runtime.repository.WorkItemStore workItemStore;

    @Inject
    io.casehub.work.runtime.filter.LabelRuleEngine labelRuleEngine;

    public record CreateLabelRuleRequest(String name, String description,
            String conditionLanguage, String conditionExpression,
            List<LabelActionDto> actions, String triggerEvents, String scope) {}

    public record LabelActionDto(String type, String label) {}

    @GET
    public List<Map<String, Object>> list() {
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (LabelRuleEntity r : labelRuleStore.scanAll()) {
            result.add(toPersistedResponse(r));
        }
        permanentRules.forEach(r -> result.add(toPermanentResponse(r)));
        return result;
    }

    @POST
    @Transactional
    public Response create(final CreateLabelRuleRequest req) {
        if (req == null || req.name() == null || req.name().isBlank()) {
            return Response.status(400).entity(Map.of("error", "name required")).build();
        }
        if (req.conditionLanguage() == null || req.conditionLanguage().isBlank()) {
            return Response.status(400).entity(Map.of("error", "conditionLanguage required")).build();
        }
        if (req.conditionExpression() == null || req.conditionExpression().isBlank()) {
            return Response.status(400).entity(Map.of("error", "conditionExpression required")).build();
        }

        try {
            expressionRegistry.validate(req.conditionLanguage(), req.conditionExpression());
        } catch (Exception e) {
            return Response.status(400)
                    .entity(Map.of("error", "Invalid expression: " + e.getMessage())).build();
        }

        final LabelRuleEntity rule = new LabelRuleEntity();
        rule.name = req.name();
        rule.description = req.description();
        rule.conditionLanguage = req.conditionLanguage();
        rule.conditionExpression = req.conditionExpression();
        rule.actionsJson = req.actions() != null
                ? LabelRuleEntity.serializeActions(toActions(req.actions()))
                : "[]";
        rule.triggerEvents = req.triggerEvents() != null ? req.triggerEvents() : "";
        if (req.scope() != null && !req.scope().isBlank()) {
            rule.scope = io.casehub.platform.api.path.Path.parse(req.scope());
        }
        rule.enabled = true;
        labelRuleStore.put(rule);
        return Response.status(201)
                .entity(Map.of("id", rule.id, "name", rule.name, "enabled", rule.enabled))
                .build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public Response update(@PathParam("id") final UUID id, final CreateLabelRuleRequest req) {
        final LabelRuleEntity rule = labelRuleStore.get(id).orElse(null);
        if (rule == null) {
            return Response.status(404).entity(Map.of("error", "Not found")).build();
        }
        if (req.name() != null) {
            rule.name = req.name();
        }
        if (req.conditionExpression() != null) {
            rule.conditionExpression = req.conditionExpression();
        }
        if (req.actions() != null) {
            rule.actionsJson = LabelRuleEntity.serializeActions(toActions(req.actions()));
        }
        if (req.description() != null) {
            rule.description = req.description();
        }
        if (req.triggerEvents() != null) {
            rule.triggerEvents = req.triggerEvents();
        }
        return Response.ok(Map.of("id", rule.id, "name", rule.name)).build();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("id") final UUID id) {
        if (!labelRuleStore.delete(id)) {
            return Response.status(404).entity(Map.of("error", "Not found")).build();
        }
        for (var wi : workItemStore.scanAll()) {
            labelRuleEngine.evaluate(wi, io.casehub.work.runtime.event.WorkItemContextBuilder.toMap(wi), "UPDATE");
            workItemStore.put(wi);
        }
        return Response.noContent().build();
    }

    @POST
    @Path("/evaluate")
    public Response evaluate(final Map<String, Object> req) {
        final String language = (String) req.get("conditionLanguage");
        final String expression = (String) req.get("conditionExpression");
        if (language == null || expression == null) {
            return Response.status(400)
                    .entity(Map.of("error", "conditionLanguage and conditionExpression required")).build();
        }
        try {
            @SuppressWarnings("unchecked")
            var compiled = expressionRegistry.compile(language, expression,
                    (Class<Map<String, Object>>) (Class<?>) Map.class, Boolean.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> context = (Map<String, Object>) req.getOrDefault("context", Map.of());
            Boolean result = compiled.eval(context);
            return Response.ok(Map.of("matches", Boolean.TRUE.equals(result))).build();
        } catch (Exception e) {
            return Response.status(400)
                    .entity(Map.of("error", e.getMessage())).build();
        }
    }

    private List<LabelAction> toActions(List<LabelActionDto> dtos) {
        return dtos.stream().map(dto -> {
            if ("Add".equals(dto.type())) {
                return (LabelAction) new LabelAction.Add(dto.label());
            } else {
                return (LabelAction) new LabelAction.Remove(dto.label());
            }
        }).toList();
    }

    private Map<String, Object> toPersistedResponse(final LabelRuleEntity r) {
        final Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.id);
        m.put("name", r.name);
        m.put("description", r.description);
        m.put("enabled", r.enabled);
        m.put("conditionLanguage", r.conditionLanguage);
        m.put("conditionExpression", r.conditionExpression);
        m.put("actionsJson", r.actionsJson);
        m.put("triggerEvents", r.triggerEvents);
        m.put("scope", r.scope != null ? r.scope.value() : null);
        m.put("source", "persisted");
        m.put("createdAt", r.createdAt);
        return m;
    }

    private Map<String, Object> toPermanentResponse(final LabelRule r) {
        final Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", r.name());
        m.put("triggerEvents", r.triggerEvents());
        m.put("source", "permanent");
        return m;
    }
}
