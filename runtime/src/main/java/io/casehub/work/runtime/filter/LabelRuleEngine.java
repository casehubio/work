package io.casehub.work.runtime.filter;

import io.casehub.platform.api.expression.ExpressionEngineRegistry;
import io.casehub.platform.api.label.LabelAction;
import io.casehub.platform.api.label.LabelRule;
import io.casehub.work.api.LabelPersistence;
import io.casehub.work.runtime.model.WorkItemEntity;
import io.casehub.work.runtime.model.WorkItemLabelEntity;
import io.casehub.work.runtime.repository.LabelRuleStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class LabelRuleEngine {

    private static final Logger LOG = Logger.getLogger(LabelRuleEngine.class);
    private static final ThreadLocal<Boolean> RUNNING = ThreadLocal.withInitial(() -> false);

    @Inject
    Instance<LabelRule> cdiRules;

    @Inject
    LabelRuleStore labelRuleStore;

    @Inject
    ExpressionEngineRegistry expressionRegistry;
    @Inject
    Event<LabelChangeEvent>  labelChangeEvent;


    private List<LabelRule> testRules;

    LabelRuleEngine() {}

    LabelRuleEngine(List<LabelRule> testRules) {
        this.testRules = testRules;
    }

    public void evaluate(final WorkItemEntity workItem, final Map<String, Object> context,
                         final String event) {
        if (Boolean.TRUE.equals(RUNNING.get())) {
            return;
        }
        RUNNING.set(true);
        try {
            Set<String> before = workItem.labels.stream()
                                                .filter(l -> l.persistence == LabelPersistence.INFERRED)
                                                .map(l -> l.path)
                                                .collect(Collectors.toSet());

            workItem.labels.removeIf(l -> l.persistence == LabelPersistence.INFERRED);

            List<LabelRule>     allRules       = collectRules();
            Map<String, Object> currentContext = io.casehub.work.runtime.event.WorkItemContextBuilder.toMap(io.casehub.work.runtime.repository.WorkItemEntityMapper.toDomain(workItem));
            int                 maxPasses      = 5;
            for (int pass = 0; pass < maxPasses; pass++) {
                List<RuleAction> matched    = evaluateRules(allRules, currentContext, event);
                int              beforeSize = workItem.labels.size();
                applyActions(workItem, matched);
                if (workItem.labels.size() == beforeSize) {
                    break;
                }
                currentContext = io.casehub.work.runtime.event.WorkItemContextBuilder.toMap(io.casehub.work.runtime.repository.WorkItemEntityMapper.toDomain(workItem));
            }

            Set<String> after = workItem.labels.stream()
                                               .filter(l -> l.persistence == LabelPersistence.INFERRED)
                                               .map(l -> l.path)
                                               .collect(Collectors.toSet());

            List<LabelChangeEvent.LabelDelta> deltas = new ArrayList<>();
            for (String path : after) {
                if (!before.contains(path)) {
                    deltas.add(new LabelChangeEvent.LabelDelta(path,
                                                               LabelChangeEvent.ChangeType.ADDED));
                }
            }
            for (String path : before) {
                if (!after.contains(path)) {
                    deltas.add(new LabelChangeEvent.LabelDelta(path,
                                                               LabelChangeEvent.ChangeType.REMOVED));
                }
            }

            if (!deltas.isEmpty() && labelChangeEvent != null) {
                labelChangeEvent.fire(new LabelChangeEvent(workItem, deltas));
            }
        } finally {
            RUNNING.remove();
        }
    }

    private List<LabelRule> collectRules() {
        List<LabelRule> all = new ArrayList<>();
        if (testRules != null) {
            all.addAll(testRules);
        } else {
            cdiRules.forEach(all::add);
            for (LabelRuleEntity entity : labelRuleStore.findEnabled()) {
                try {
                    all.add(entity.toLabelRule(expressionRegistry));
                } catch (Exception e) {
                    LOG.warnf("Failed to compile label rule '%s': %s", entity.name, e.getMessage());
                }
            }
        }
        return all;
    }

    private List<RuleAction> evaluateRules(final List<LabelRule> rules,
            final Map<String, Object> context, final String event) {
        List<RuleAction> result = new ArrayList<>();
        for (LabelRule rule : rules) {
            Set<String> triggers = rule.triggerEvents();
            if (!triggers.isEmpty() && !triggers.contains(event)) {
                continue;
            }
            try {
                if (Boolean.TRUE.equals(rule.condition().eval(context))) {
                    for (LabelAction action : rule.actions()) {
                        result.add(new RuleAction(rule.name(), action));
                    }
                }
            } catch (Exception e) {
                LOG.warnf("Rule '%s' evaluation failed: %s", rule.name(), e.getMessage());
            }
        }
        return result;
    }

    private void applyActions(final WorkItemEntity workItem, final List<RuleAction> ruleActions) {
        for (RuleAction ra : ruleActions) {
            if (ra.action instanceof LabelAction.Add add) {
                boolean exists = workItem.labels.stream()
                        .anyMatch(l -> add.label().equals(l.path) && l.persistence == LabelPersistence.INFERRED);
                if (!exists) {
                    workItem.labels.add(new WorkItemLabelEntity(add.label(), LabelPersistence.INFERRED, ra.ruleName));
                }
            } else if (ra.action instanceof LabelAction.Remove remove) {
                workItem.labels.removeIf(l ->
                        remove.label().equals(l.path) && l.persistence == LabelPersistence.INFERRED);
            }
        }
    }

    private record RuleAction(String ruleName, LabelAction action) {}
}
