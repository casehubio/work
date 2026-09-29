package io.casehub.work.rest.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.casehub.platform.api.expression.ExpressionEngineRegistry;
import io.casehub.platform.api.label.LabelAction;
import io.casehub.platform.api.label.LabelRule;
import io.casehub.platform.api.path.Path;
import io.casehub.work.api.spi.WorkItemLabelRuleApi;
import io.casehub.work.api.spi.WorkItemStore;
import io.casehub.work.api.view.CreateLabelRuleRequest;
import io.casehub.work.api.view.EvaluateExpressionRequest;
import io.casehub.work.api.view.EvaluateExpressionResult;
import io.casehub.work.api.view.LabelActionDto;
import io.casehub.work.api.view.LabelRuleView;
import io.casehub.work.runtime.event.WorkItemContextBuilder;
import io.casehub.work.runtime.filter.LabelRuleEngine;
import io.casehub.work.runtime.filter.LabelRuleEntity;
import io.casehub.work.runtime.repository.LabelRuleStore;
import io.casehub.work.runtime.repository.WorkItemEntityMapper;

@ApplicationScoped
public class DefaultWorkItemLabelRuleApi implements WorkItemLabelRuleApi {

    @Inject
    LabelRuleStore labelRuleStore;

    @Inject
    ExpressionEngineRegistry expressionRegistry;

    @Inject
    Instance<LabelRule> permanentRules;

    @Inject
    WorkItemStore workItemStore;

    @Inject
    LabelRuleEngine labelRuleEngine;

    @Override
    public List<LabelRuleView> list(String tenancyId) {
        List<LabelRuleView> result = new ArrayList<>();
        for (LabelRuleEntity r : labelRuleStore.scanAll()) {
            result.add(toPersistedView(r));
        }
        permanentRules.forEach(r -> result.add(toPermanentView(r)));
        return result;
    }

    @Override
    @Transactional
    public LabelRuleView create(CreateLabelRuleRequest request, String tenancyId) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (request.conditionLanguage() == null || request.conditionLanguage().isBlank()) {
            throw new IllegalArgumentException("conditionLanguage is required");
        }
        if (request.conditionExpression() == null || request.conditionExpression().isBlank()) {
            throw new IllegalArgumentException("conditionExpression is required");
        }

        expressionRegistry.validate(request.conditionLanguage(), request.conditionExpression());

        final LabelRuleEntity rule = new LabelRuleEntity();
        rule.tenancyId = tenancyId;
        rule.name = request.name();
        rule.description = request.description();
        rule.conditionLanguage = request.conditionLanguage();
        rule.conditionExpression = request.conditionExpression();
        rule.actionsJson = request.actions() != null
                ? LabelRuleEntity.serializeActions(toActions(request.actions()))
                : "[]";
        rule.triggerEvents = request.triggerEvents() != null ? request.triggerEvents() : "";
        if (request.scope() != null && !request.scope().isBlank()) {
            rule.scope = Path.parse(request.scope());
        }
        rule.enabled = true;
        labelRuleStore.put(rule);
        return toPersistedView(rule);
    }

    @Override
    @Transactional
    public LabelRuleView update(UUID ruleId, CreateLabelRuleRequest request, String tenancyId) {
        final LabelRuleEntity rule = labelRuleStore.get(ruleId).orElse(null);
        if (rule == null) {
            return null;
        }
        if (request.name() != null) {
            rule.name = request.name();
        }
        if (request.conditionExpression() != null) {
            rule.conditionExpression = request.conditionExpression();
        }
        if (request.actions() != null) {
            rule.actionsJson = LabelRuleEntity.serializeActions(toActions(request.actions()));
        }
        if (request.description() != null) {
            rule.description = request.description();
        }
        if (request.triggerEvents() != null) {
            rule.triggerEvents = request.triggerEvents();
        }
        return toPersistedView(rule);
    }

    @Override
    @Transactional
    public void delete(UUID ruleId, String tenancyId) {
        if (!labelRuleStore.delete(ruleId)) {
            throw new IllegalArgumentException("Label rule not found");
        }
        for (var wi : workItemStore.scanAll()) {
            final var entity = WorkItemEntityMapper.toEntity(wi);
            labelRuleEngine.evaluate(entity, WorkItemContextBuilder.toMap(wi), "UPDATE");
            workItemStore.put(WorkItemEntityMapper.toDomain(entity));
        }
    }

    @Override
    public EvaluateExpressionResult evaluate(EvaluateExpressionRequest request, String tenancyId) {
        if (request.conditionLanguage() == null || request.conditionExpression() == null) {
            throw new IllegalArgumentException("conditionLanguage and conditionExpression are required");
        }
        @SuppressWarnings("unchecked")
        var compiled = expressionRegistry.compile(request.conditionLanguage(), request.conditionExpression(),
                (Class<Map<String, Object>>) (Class<?>) Map.class, Boolean.class);
        Map<String, Object> context = request.context() != null ? request.context() : Map.of();
        Boolean result = compiled.eval(context);
        return new EvaluateExpressionResult(Boolean.TRUE.equals(result));
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

    private LabelRuleView toPersistedView(LabelRuleEntity r) {
        return new LabelRuleView(
                r.id, r.name, r.description, r.enabled,
                r.conditionLanguage, r.conditionExpression, r.actionsJson,
                r.triggerEvents, r.scope != null ? r.scope.value() : null,
                "persisted", r.createdAt);
    }

    private LabelRuleView toPermanentView(LabelRule r) {
        return new LabelRuleView(
                null, r.name(), null, true,
                null, null, null,
                r.triggerEvents() != null ? String.join(",", r.triggerEvents()) : null, null,
                "permanent", null);
    }
}
