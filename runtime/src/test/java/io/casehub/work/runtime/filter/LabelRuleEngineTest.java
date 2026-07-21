package io.casehub.work.runtime.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.platform.api.expression.ExpressionEvaluationException;
import io.casehub.platform.api.expression.LambdaExpression;
import io.casehub.platform.api.label.LabelAction;
import io.casehub.platform.api.label.LabelRule;
import io.casehub.work.api.LabelPersistence;
import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemLabel;

class LabelRuleEngineTest {

    private LabelRuleEngine engine;
    private List<LabelRule> cdiRules;

    @BeforeEach
    void setUp() {
        cdiRules = new ArrayList<>();
        engine = new LabelRuleEngine(cdiRules);
    }

    @Test
    void matchingRule_appliesInferredLabel() {
        cdiRules.add(new LabelRule("urgent-rule",
                new LambdaExpression<>(ctx -> {
                    Object p = ctx.get("priority");
                    return p instanceof WorkItemPriority wp && wp == WorkItemPriority.HIGH;
                }),
                List.of(new LabelAction.Add("priority/high"))));

        var wi = new WorkItem();
        wi.priority = WorkItemPriority.HIGH;

        engine.evaluate(wi, Map.of("priority", wi.priority), "ADD");

        assertThat(wi.labels).extracting(l -> l.path).contains("priority/high");
        assertThat(wi.labels).filteredOn(l -> l.path.equals("priority/high"))
                .extracting(l -> l.persistence).containsOnly(LabelPersistence.INFERRED);
    }

    @Test
    void nonMatchingRule_doesNotApplyLabel() {
        cdiRules.add(new LabelRule("urgent-rule",
                new LambdaExpression<>(ctx -> false),
                List.of(new LabelAction.Add("priority/high"))));

        var wi = new WorkItem();
        engine.evaluate(wi, Map.of(), "ADD");

        assertThat(wi.labels).extracting(l -> l.path).doesNotContain("priority/high");
    }

    @Test
    void stripsInferredLabels_beforeEvaluation() {
        var wi = new WorkItem();
        wi.labels.add(new WorkItemLabel("old/inferred", LabelPersistence.INFERRED, "old-rule"));
        wi.labels.add(new WorkItemLabel("manual/keep", LabelPersistence.MANUAL, "alice"));

        engine.evaluate(wi, Map.of(), "ADD");

        assertThat(wi.labels).extracting(l -> l.path).doesNotContain("old/inferred");
        assertThat(wi.labels).extracting(l -> l.path).contains("manual/keep");
    }

    @Test
    void removeAction_removesOnlyInferredLabels() {
        cdiRules.add(new LabelRule("cleanup-rule",
                new LambdaExpression<>(ctx -> true),
                List.of(new LabelAction.Remove("shared-label"))));

        var wi = new WorkItem();
        wi.labels.add(new WorkItemLabel("shared-label", LabelPersistence.MANUAL, "alice"));

        engine.evaluate(wi, Map.of(), "ADD");

        assertThat(wi.labels).extracting(l -> l.path).contains("shared-label");
    }

    @Test
    void addAction_deduplicatesExistingInferred() {
        cdiRules.add(new LabelRule("r1",
                new LambdaExpression<>(ctx -> true),
                List.of(new LabelAction.Add("x"))));
        cdiRules.add(new LabelRule("r2",
                new LambdaExpression<>(ctx -> true),
                List.of(new LabelAction.Add("x"))));

        var wi = new WorkItem();
        engine.evaluate(wi, Map.of(), "ADD");

        long count = wi.labels.stream().filter(l -> l.path.equals("x")).count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void triggerEvents_filtersRules() {
        cdiRules.add(new LabelRule("add-only",
                new LambdaExpression<>(ctx -> true),
                List.of(new LabelAction.Add("on-add")),
                Set.of("ADD")));

        var wi = new WorkItem();
        engine.evaluate(wi, Map.of(), "REMOVE");

        assertThat(wi.labels).extracting(l -> l.path).doesNotContain("on-add");
    }

    @Test
    void triggerEvents_emptyMatchesAll() {
        cdiRules.add(new LabelRule("all-events",
                new LambdaExpression<>(ctx -> true),
                List.of(new LabelAction.Add("always"))));

        var wi = new WorkItem();
        engine.evaluate(wi, Map.of(), "REMOVE");

        assertThat(wi.labels).extracting(l -> l.path).contains("always");
    }

    @Test
    void perRuleErrorIsolation_continuesAfterFailure() {
        cdiRules.add(new LabelRule("bad-rule",
                new LambdaExpression<>(ctx -> {
                    throw new ExpressionEvaluationException("boom");
                }),
                List.of(new LabelAction.Add("bad"))));
        cdiRules.add(new LabelRule("good-rule",
                new LambdaExpression<>(ctx -> true),
                List.of(new LabelAction.Add("good"))));

        var wi = new WorkItem();
        engine.evaluate(wi, Map.of(), "ADD");

        assertThat(wi.labels).extracting(l -> l.path).doesNotContain("bad");
        assertThat(wi.labels).extracting(l -> l.path).contains("good");
    }

    @Test
    void appliedBy_setToRuleName() {
        cdiRules.add(new LabelRule("my-rule",
                new LambdaExpression<>(ctx -> true),
                List.of(new LabelAction.Add("tagged"))));

        var wi = new WorkItem();
        engine.evaluate(wi, Map.of(), "ADD");

        assertThat(wi.labels).filteredOn(l -> l.path.equals("tagged"))
                .extracting(l -> l.appliedBy).containsOnly("my-rule");
    }

    @Test
    void reentrancyGuard_preventsRecursiveEvaluation() {
        cdiRules.add(new LabelRule("reentrant-rule",
                new LambdaExpression<>(ctx -> true),
                List.of(new LabelAction.Add("first"))));

        var wi = new WorkItem();
        engine.evaluate(wi, Map.of(), "ADD");

        assertThat(wi.labels).extracting(l -> l.path).contains("first");
    }

    @Test
    void multipleActions_allApplied() {
        cdiRules.add(new LabelRule("multi-action",
                new LambdaExpression<>(ctx -> true),
                List.of(new LabelAction.Add("tier/urgent"),
                        new LabelAction.Add("tier/urgent/unassigned"))));

        var wi = new WorkItem();
        engine.evaluate(wi, Map.of(), "ADD");

        assertThat(wi.labels).extracting(l -> l.path)
                .contains("tier/urgent", "tier/urgent/unassigned");
    }
}
